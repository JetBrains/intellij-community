// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.net.PlatformHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Properties
import kotlin.random.Random

private const val CONFIG_PROPERTY: String = "se.ranker.eval.config"
private const val DATASET_PROPERTY: String = "se.ranker.eval.dataset"
private const val REPORT_PROPERTY: String = "se.ranker.eval.report"
private const val LIMIT_PROPERTY: String = "se.ranker.eval.limit"
private const val CONCURRENCY_PROPERTY: String = "se.ranker.eval.concurrency"

private const val DEFAULT_CONCURRENCY: Int = 4
private const val SAMPLE_SEED: Long = 259052
private const val REPORT_FILE_NAME: String = "se-actions-ranker-evaluation.md"

/**
 * Compares the Jev ranker and the Decision API ranker on the dataset of natural-language action search, and writes a Markdown report.
 * See [SeRankerEvaluator] for how it ranks and times the requests, and [renderRankerReport] for the report.
 *
 * The test sends real requests that cost money. Thus it runs only when the `se.ranker.eval.config` system property names a properties file.
 * The file holds the personal keys: `jev.key` for a TypeSafe or an OpenRouter key, and `decisions.key` for an OpenAI key.
 * The test skips a ranker without a key. `jev.model` and `decisions.model` override the default models.
 * Keep the file out of the repository.
 *
 * The file can also hold the settings below, with the same names. A system property with the same name overrides the file.
 * The test prints the settings before the first request.
 * - `se.ranker.eval.report`: the path of the report.
 *   The default is `se-actions-ranker-evaluation.md` in the Bazel test outputs, or else in the temporary directory.
 * - `se.ranker.eval.dataset`: the path of the dataset. The default is `testData/actionsRanker/actions-nl-dataset.yaml` of this module.
 * - `se.ranker.eval.limit`: the number of requests in a fixed random sample of the dataset. The default is all requests.
 * - `se.ranker.eval.concurrency`: the number of requests that each ranker ranks at the same time. The default is 4.
 *
 * A run of all requests takes about 3 HTTP requests for each request and ranker, and can take an hour.
 * Run it from the IDE with `-Dse.ranker.eval.config=<file>` in the VM options, or with bt:
 * ```
 * ./community/tools/bt.cmd --module intellij.platform.searchEverywhere.frontend.tests \
 *   --filter com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsRankerEvaluationTest --no-cache -- \
 *   --test_timeout=7200 --test_arg=--jvm_flag=-Dse.ranker.eval.config=$HOME/se-ranker-evaluation.properties
 * ```
 */
@TestApplication
@EnabledIfSystemProperty(named = CONFIG_PROPERTY, matches = ".+")
class SeActionsRankerEvaluationTest {
  @Test
  fun `compare the rankers on the dataset`(): Unit = runBlocking {
    val configPath = Path.of(System.getProperty(CONFIG_PROPERTY))
    val config = Properties().apply { Files.newBufferedReader(configPath).use { load(it) } }
    val rankers = listOfNotNull(jevRanker(config), decisionsRanker(config))
    check(rankers.isNotEmpty()) { "$configPath has neither jev.key nor decisions.key" }

    val datasetPath = config.setting(DATASET_PROPERTY)?.let { Path.of(it) } ?: SeRankerDataset.defaultPath()
    val dataset = SeRankerDataset.load(datasetPath)
    val limit = config.intSetting(LIMIT_PROPERTY)
    val requests = if (limit == null) dataset.requests else dataset.requests.shuffled(Random(SAMPLE_SEED)).take(limit)
    val concurrency = config.intSetting(CONCURRENCY_PROPERTY) ?: DEFAULT_CONCURRENCY
    val reportPath = reportPath(config)
    println("Ranks ${requests.size} of ${dataset.requests.size} requests with ${rankers.joinToString()}, " +
            "$concurrency at the same time for each ranker. The report goes to $reportPath")

    val client = PlatformHttpClient.client()
    val runs = try {
      // The production provider also sends the candidates in the order of their ids.
      val evaluator = SeRankerEvaluator(client, dataset.actions.sortedBy { it.id }, concurrency, progress = ::println)
      rankers.map { evaluator.evaluate(it, requests) }
    }
    finally {
      client.shutdownNow()
    }

    val settings = linkedMapOf(
      "Date" to ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      "Dataset file" to "$datasetPath",
      "Evaluated requests" to if (limit == null) "all ${requests.size}" else "a random sample of ${requests.size}",
      "Candidates strategy" to SeActionsCandidatesStrategy.SPLIT_AND_MERGE.registryOption,
      "Concurrency" to "$concurrency requests at the same time for each ranker",
    )
    val report = renderRankerReport(dataset, settings, runs)
    reportPath.parent?.let { Files.createDirectories(it) }
    Files.writeString(reportPath, report)
    println("The report is in $reportPath")
    println(report)

    assertTrue(runs.all { it.rejection == null }) { runs.mapNotNull { it.rejection }.joinToString(" ") }
  }
}

private fun jevRanker(config: Properties): SeEvaluatedRanker? {
  val key = config.value("jev.key") ?: return null
  val route = SeJevRoute.forKey(key)
  val model = config.value("jev.model") ?: route.defaultModel
  return SeEvaluatedRanker(SeJevActionsRanker().diagnosticName, route.api, route.baseUrl, model, key)
}

private fun decisionsRanker(config: Properties): SeEvaluatedRanker? {
  val key = config.value("decisions.key") ?: return null
  val model = config.value("decisions.model") ?: DECISIONS_DEFAULT_MODEL
  return SeEvaluatedRanker(SeDecisionsActionsRanker().diagnosticName, SeDecisionsApi.OPENAI, OPENAI_API_URL, model, key)
}

private fun Properties.value(name: String): String? = getProperty(name)?.trim()?.ifEmpty { null }

/** Returns the setting [name] from the system properties, or else from the configuration file. */
private fun Properties.setting(name: String): String? = System.getProperty(name)?.trim()?.ifEmpty { null } ?: value(name)

private fun Properties.intSetting(name: String): Int? {
  val value = setting(name) ?: return null
  return value.toIntOrNull() ?: error("$name must be a number, but it is '$value'")
}

private fun reportPath(config: Properties): Path {
  config.setting(REPORT_PROPERTY)?.let { return Path.of(it) }
  val directory = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR") ?: System.getProperty("java.io.tmpdir")
  return Path.of(directory, REPORT_FILE_NAME)
}
