// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.teamCity

import jetbrains.buildServer.messages.serviceMessages.PublishArtifacts
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.UUID
import java.net.URLEncoder

/**
 * Builds and prints [TeamCity service messages](https://www.jetbrains.com/help/teamcity/service-messages.html).
 *
 * All attribute values are escaped automatically.
 */
@ApiStatus.Internal
object TeamCityReporter {

  private const val MAX_TEST_NAME_LENGTH: Int = 250

  /**
   * Kind of a synthetic test reported via [reportTestLifecycle]. Determines the prefix
   * wrapped around the test name so the three groups can be told apart on TeamCity
   * (for muting, ownership, dashboards, etc.).
   *
   * - [IDE_EXCEPTION]: unhandled IDE exception / freeze / timeout captured from the IDE
   *   under test.
   * - [TEST_INFRA_EXCEPTION]: failure of the test infrastructure itself - port
   *   allocation, leftover processes, VM options validation, missing IDE directories,
   *   etc. These are harness bugs, not IDE bugs, and should be muted / owned separately.
   * - [SOFT_ASSERT_FAILURE]: a soft-assert-style failure reported from inside a test that
   *   does not want to fail the whole test but still wants to surface a test-like
   *   entry on TeamCity.
   */
  enum class SyntheticTestKind(val prefix: String) {
    IDE_EXCEPTION("IdeException"),
    TEST_INFRA_EXCEPTION("TestInfraException"),
    SOFT_ASSERT_FAILURE("SoftAssertFailure"),
  }

  private fun String.syntheticTestName(kind: SyntheticTestKind): String =
    "(${kind.prefix} $this)"

  /**
   * Truncates and escapes a string for safe inclusion in a TeamCity service message value.
   * Uses the same escaping as [ServiceMessage.asString] to guarantee consistency.
   */
  fun String.processedForTC(): String {
    // Use ServiceMessage.asString to ensure identical escaping, then extract the escaped value.
    val raw = ServiceMessage.asString("_", this)
    return raw.removePrefix("##teamcity[_ '").removeSuffix("']")
  }

  /**
   * Builds a TeamCity service message with key-value attributes. Prefer the typed
   * [reportTestStarted], [reportTestFailed], etc. for test lifecycle messages.
   * Use this for dynamic or uncommon message types (e.g. `buildStatus`, `buildProblem`).
   *
   * Example: `serviceMessage("testStarted", mapOf("name" to "myTest", "flowId" to "123"))`
   * produces `##teamcity[testStarted name='myTest' flowId='123']`
   *
   * @param messageName the TeamCity message type (e.g. `"testStarted"`, `"buildStatus"`)
   * @param attributes  key-value pairs; values are escaped automatically, `null` values are skipped,
   *                    keys are not escaped (they are identifiers)
   * @return the formatted `##teamcity[…]` string
   */
  fun serviceMessage(messageName: String, attributes: Map<String, String>): String =
    ServiceMessage.asString(messageName, attributes)

  /**
   * Prints a `##teamcity[testStarted …]`
   * [test reporting](https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Tests) message to stdout.
   *
   * @param testName               the test name
   * @param flowId                 optional [flow identifier](https://www.jetbrains.com/help/teamcity/service-messages.html#Message+FlowId)
   *                               for parallel output
   * @param nodeId                 optional tree node identifier (for test-tree hierarchy)
   * @param parentNodeId           optional parent node identifier (for test-tree hierarchy)
   * @param captureStandardOutput  if `true`, TeamCity captures stdout/stderr for this test
   */
  private fun reportTestStarted(
    testName: String, flowId: String? = null, nodeId: String? = null,
    parentNodeId: String? = null, captureStandardOutput: Boolean? = null,
  ) {
    println(serviceMessage("testStarted", buildMap {
      put("name", testName)
      flowId?.let { put("flowId", it) }
      nodeId?.let { put("nodeId", it) }
      parentNodeId?.let { put("parentNodeId", it) }
      captureStandardOutput?.let { put("captureStandardOutput", it.toString()) }
    }))
  }

  /**
   * Prints a `##teamcity[testFinished …]` message to stdout.
   *
   * @param testName     the test name
   * @param flowId       optional flow identifier for parallel output
   * @param nodeId       optional tree node identifier (for test-tree hierarchy)
   * @param parentNodeId optional parent node identifier (for test-tree hierarchy)
   * @param duration     optional test duration in milliseconds (as a string)
   */
  private fun reportTestFinished(
    testName: String, flowId: String? = null, nodeId: String? = null,
    parentNodeId: String? = null, duration: String? = null,
  ) {
    println(serviceMessage("testFinished", buildMap {
      put("name", testName)
      flowId?.let { put("flowId", it) }
      nodeId?.let { put("nodeId", it) }
      parentNodeId?.let { put("parentNodeId", it) }
      duration?.let { put("duration", it) }
    }))
  }

  /**
   * Prints a `##teamcity[testFailed …]` message to stdout.
   *
   * @param testName     the test name
   * @param message      the failure message
   * @param flowId       optional flow identifier for parallel output
   * @param nodeId       optional tree node identifier (for test-tree hierarchy)
   * @param parentNodeId optional parent node identifier (for test-tree hierarchy)
   * @param details      optional failure details / stack trace
   */
  private fun reportTestFailed(
    testName: String, message: String, flowId: String? = null,
    nodeId: String? = null, parentNodeId: String? = null, details: String? = null,
  ) {
    println(serviceMessage("testFailed", buildMap {
      put("name", testName)
      put("message", message)
      details?.let { put("details", it) }
      flowId?.let { put("flowId", it) }
      nodeId?.let { put("nodeId", it) }
      parentNodeId?.let { put("parentNodeId", it) }
    }))
  }

  /**
   * Prints a `##teamcity[testIgnored …]` message to stdout.
   *
   * @param testName     the test name
   * @param message      the reason the test was ignored
   * @param flowId       optional flow identifier for parallel output
   * @param nodeId       optional tree node identifier (for test-tree hierarchy)
   * @param parentNodeId optional parent node identifier (for test-tree hierarchy)
   */
  private fun reportTestIgnored(
    testName: String, message: String, flowId: String? = null,
    nodeId: String? = null, parentNodeId: String? = null,
  ) {
    println(serviceMessage("testIgnored", buildMap {
      put("name", testName)
      put("message", message)
      flowId?.let { put("flowId", it) }
      nodeId?.let { put("nodeId", it) }
      parentNodeId?.let { put("parentNodeId", it) }
    }))
  }

  /** Prints a `##teamcity[testSuiteStarted …]` message to stdout. */
  private fun reportTestSuiteStarted(suiteName: String, flowId: String? = null) {
    println(TestSuiteStarted(suiteName).apply { flowId?.let { setFlowId(it) } }.asString())
  }

  /** Prints a `##teamcity[testSuiteFinished …]` message to stdout. */
  private fun reportTestSuiteFinished(suiteName: String, flowId: String? = null) {
    println(TestSuiteFinished(suiteName).apply { flowId?.let { setFlowId(it) } }.asString())
  }

  /**
   * Type of [test metadata](https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Additional+Test+Data)
   * attached via [reportTestMetadata].
   */
  enum class MetadataType {
    NUMBER,
    TEXT,
    LINK,
    ARTIFACT,
    IMAGE
  }

  /**
   * Prints a `##teamcity[testMetadata …]`
   * [test metadata](https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Additional+Test+Data) message to stdout.
   *
   * @param testName the test name to attach metadata to; `null` attaches to the currently running test
   * @param value    the metadata value
   * @param name     optional human-readable label for the metadata entry
   * @param flowId   optional flow identifier for parallel output
   * @param type     the metadata type
   */
  fun reportTestMetadata(
    testName: String? = null, value: String, name: String? = null,
    flowId: String? = null, type: MetadataType = MetadataType.TEXT,
  ) {
    println(serviceMessage("testMetadata", buildMap {
      testName?.let { put("testName", it) }
      put("type", type.name.lowercase())
      name?.let { put("name", it) }
      put("value", value)
      flowId?.let { put("flowId", it) }
    }))
  }

  /**
   * Prints a `##teamcity[buildStatisticValue …]`
   * [statistic value](https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Build+Statistics) message to stdout.
   */
  fun reportStatisticValue(key: String, value: Any) {
    println(serviceMessage("buildStatisticValue", mapOf("key" to key, "value" to value.toString())))
  }

  /**
   * Prints a `##teamcity[publishArtifacts …]`
   * [artifact publishing](https://www.jetbrains.com/help/teamcity/service-messages.html#Publishing+Artifacts+While+Build+is+in+Progress) message to stdout.
   *
   * @param spec artifact path specification (e.g. `"path/to/file => destination"`)
   */
  fun reportPublishArtifacts(spec: String) {
    // https://www.jetbrains.com/help/teamcity/2025.07/configuring-general-settings.html#Artifact+Paths
    // > You can specify exact file paths or patterns, one per line or comma-separated.
    // Because of that feature, files and directories with a comma in the name can't be mentioned as is.
    // So, commas are replaced with wildcards.
    // See also TW-19333.
    println(PublishArtifacts(spec.replace(",", "*")).asString())
  }

  /**
   * Prints a `##teamcity[publishArtifacts …]`
   * [artifact publishing](https://www.jetbrains.com/help/teamcity/service-messages.html#Publishing+Artifacts+While+Build+is+in+Progress) message to stdout.
   *
   * @param artifactPath path to the artifact file or directory
   * @param artifactName optional target name in the artifact storage; if `null`, the original file name is used
   */
  fun reportPublishArtifacts(artifactPath: Path, artifactName: String? = null) {
    val spec = if (artifactName != null) "$artifactPath=>$artifactName" else artifactPath.toString()
    reportPublishArtifacts(spec)
  }

  /** Outcome of a test reported via [reportTestLifecycle]. */
  enum class TestOutcome { SUCCESS, FAILED, IGNORED }

  /**
   * A metadata entry to attach to a test during [reportTestLifecycle].
   */
  data class TestMetadata(
    val name: String? = null,
    val value: String,
    val type: MetadataType = MetadataType.TEXT,
  )

  /**
   * Reports a complete test lifecycle (started → outcome → metadata → finished) to stdout.
   *
   * The test is placed under the root node (`parentNodeId = "0"`).
   *
   * @param testName         the test name
   * @param outcome          the test result
   * @param message          failure or ignore reason (unused for [TestOutcome.SUCCESS])
   * @param details          failure details / stack trace (unused for [TestOutcome.SUCCESS])
   * @param owner            optional code-owner metadata attached on failure
   * @param metadata         additional [TestMetadata] entries to attach
   * @param generifyTestName if `true` (default), [generifyErrorMessage] replaces volatile parts
   *                         (numbers, hashes, hex) with placeholders for stable test grouping in TC
   * @param flowId           flow id to use for all emitted messages; defaults to a fresh UUID.
   *                         Pass the flow id exposed by [reportTestSuiteLifecycle] to group this
   *                         test under that suite in TeamCity.
   * @param syntheticTestKind if non-null, wraps the test name into `(<prefix> <name>)` so the
   *                         event is reported as a synthetic test of the given [SyntheticTestKind].
   *                         `null` (default) emits the test name as-is (regular test).
   * @param block            runs between `testStarted` and `testFinished`; any stdout/stderr
   *                         produced inside is captured by TeamCity as this test's output.
   *                         Defaults to a no-op. Exceptions thrown inside [block] are caught
   *                         and logged, so the caller's intended outcome, `owner` metadata, and
   *                         [metadata] are still emitted afterwards. `testFinished` is emitted
   *                         in `finally`, so the test is always closed even if [block] throws.
   */
  fun reportTestLifecycle(
    testName: String, outcome: TestOutcome, message: String = "",
    details: String = "", owner: String? = null, metadata: List<TestMetadata> = emptyList(),
    generifyTestName: Boolean = true,
    flowId: String = UUID.randomUUID().toString(),
    syntheticTestKind: SyntheticTestKind? = null,
    block: (() -> Unit)? = null,
  ) {
    val effectiveName =
      testName
        .let { if (generifyTestName) generifyErrorMessage(it) else it }
        .take(MAX_TEST_NAME_LENGTH)
        .let { if (syntheticTestKind != null) it.syntheticTestName(syntheticTestKind) else it }

    reportTestStarted(effectiveName, flowId, nodeId = effectiveName, parentNodeId = "0", captureStandardOutput = block != null)
    try {
      runCatching { block?.invoke() }.onFailure {
        println("Block invocation failed: ${it.stackTraceToString()}")
      }
      when (outcome) {
        TestOutcome.FAILED -> {
          if (owner != null) {
            reportTestMetadata(effectiveName, owner, "Code Owner", flowId, type = MetadataType.TEXT)
            reportTestMetadata(effectiveName, "https://codeowners.labs.jb.gg/group/${URLEncoder.encode(owner, Charsets.UTF_8).replace("+", "%20")}", "'$owner' Owner Details", flowId, type = MetadataType.LINK)
          }
          reportTestFailed(effectiveName, message, flowId, nodeId = effectiveName, parentNodeId = "0", details = details)
        }
        TestOutcome.IGNORED -> {
          reportTestIgnored(effectiveName, message, flowId, nodeId = effectiveName, parentNodeId = null)
        }
        TestOutcome.SUCCESS -> {}
      }
      for (entry in metadata) {
        reportTestMetadata(effectiveName, entry.value, entry.name, flowId, entry.type)
      }
    }
    finally {
      reportTestFinished(effectiveName, flowId, nodeId = effectiveName, parentNodeId = "0", duration = null)
    }
  }

  /**
   * Reports a test-suite lifecycle (`testSuiteStarted` → [block] → `testSuiteFinished`) to stdout.
   *
   * Uses [flowId] for the suite markers (defaults to a fresh UUID) and passes it to [block].
   * Forward that flow id to nested [reportTestLifecycle] calls so TeamCity groups the tests
   * under the suite. The `testSuiteFinished` marker is always emitted, even if [block] throws.
   *
   * @param suiteName the test-suite name
   * @param flowId    flow id used for the suite markers and exposed to [block]. Defaults to a
   *                  fresh UUID; pass an existing id to share it across multiple suites.
   * @param block     receives the suite's flow id and emits the nested test events
   *                  (e.g. [reportTestLifecycle] calls that forward this flow id)
   */
  fun reportTestSuiteLifecycle(
    suiteName: String,
    flowId: String = UUID.randomUUID().toString(),
    block: (flowId: String) -> Unit,
  ) {
    reportTestSuiteStarted(suiteName, flowId)
    try {
      block(flowId)
    }
    finally {
      reportTestSuiteFinished(suiteName, flowId)
    }
  }
}