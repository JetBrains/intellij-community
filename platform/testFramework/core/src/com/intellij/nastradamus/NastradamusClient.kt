// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.TestCaseLoader
import com.intellij.nastradamus.model.*
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.HttpClient
import com.intellij.tool.mapConcurrently
import com.intellij.tool.withErrorThreshold
import com.intellij.tool.withRetry
import kotlinx.coroutines.runBlocking
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI

class NastradamusClient(
  val baseUrl: URI = URI(System.getProperty("idea.nastradamus.url")).normalize(),
  val unsortedClasses: List<Class<*>>,
  private val teamCityClient: TeamCityClient = TeamCityClient()
) {
  companion object {
    private val jacksonMapper: ObjectMapper = jacksonObjectMapper()
  }

  /** Classes for current bucket. Map<Class to Sorting order> */
  private lateinit var sortedClassesInCurrentBucket: Set<Class<*>>
  private lateinit var allSortedClasses: Map<Class<*>, Int>

  fun collectTestRunResults(): TestResultRequestEntity {
    val tests = teamCityClient.getTestRunInfo()
    val build = teamCityClient.getBuildInfo()
    val buildProperties: List<JsonNode> = build.findValue("properties")
                                            ?.findValue("property")
                                            ?.elements()?.asSequence()?.toList() ?: listOf()

    val bucketId: Int = buildProperties.firstOrNull { (it.findValue("name")?.asText() ?: "") == "system.pass.idea.test.runner.index" }
                          ?.findValue("value")?.asInt() ?: 0
    val bucketsNumber: Int = buildProperties.firstOrNull { (it.findValue("name")?.asText() ?: "") == "system.pass.idea.test.runners.count" }
                               ?.findValue("value")?.asInt() ?: 0

    val testResultEntities = tests.map { json ->
      TestResultEntity(
        name = json.findValue("name").asText(),
        status = TestStatus.fromString(json.findValue("status").asText()),
        runOrder = json.findValue("runOrder").asInt(),
        duration = json.findValue("duration")?.asLong() ?: 0,
        buildStatusMessage = build.findValue("statusText").asText(),
        isMuted = json.findValue("currentlyMuted")?.asBoolean() ?: false,
        bucketId = bucketId,
        bucketsNumber = bucketsNumber
      )
    }

    return TestResultRequestEntity(buildInfo = getBuildInfo(), testRunResults = testResultEntities)
  }

  fun sendTestRunResults(testResultRequestEntity: TestResultRequestEntity) {
    val uri = URIBuilder(baseUrl.resolve("/result/").normalize())
      .build()

    val stringJson = jacksonMapper.writeValueAsString(testResultRequestEntity)

    val httpPost = HttpPost(uri).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    println("Sending test run results to Nastradamus ...")

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $uri with payload $stringJson")
    }

    withErrorThreshold(
      objName = "NastradamusClient-sendTestRunResults",
      action = {
        withRetry {
          HttpClient.sendRequest(httpPost) { response ->
            if (response.statusLine.statusCode != 200) {
              throw RuntimeException("""
                Couldn't store test run results on Nastradamus.
                $response
                ${response.entity.content.reader().readText()}
                """.trimIndent())
            }
          }
        }
      },
      fallbackOnThresholdReached = {}
    )
  }

  /**
   * Will return tests for this particular bucket
   */
  fun sendSortingRequest(sortRequestEntity: SortRequestEntity, bucketsCount: Int, currentBucketIndex: Int): List<TestCaseEntity> {
    val uri = URIBuilder(baseUrl.resolve("/sort/").normalize())
      .addParameter("buckets", bucketsCount.toString())
      .build()

    val stringJson = jacksonMapper.writeValueAsString(sortRequestEntity)

    val httpPost = HttpPost(uri).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    println("Fetching sorted test classes from Nastradamus ...")

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $uri with payload $stringJson")
    }

    return withErrorThreshold(
      objName = "NastradamusClient-sendSortingRequest",
      action = {
        val jsonTree = withRetry {
          HttpClient.sendRequest(httpPost) {
            jacksonMapper.readTree(it.entity.content)
          }
        }

        requireNotNull(jsonTree) { "Received data from $uri must not be null" }

        if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
          println("Received data from $uri: $jsonTree")
        }

        try {
          jsonTree.fields().asSequence()
            .single { it.key == "sorted_tests" }.value
            .get(currentBucketIndex.toString())
            .map {
              TestCaseEntity(it.findValue("name").asText())
            }
        }
        catch (e: Throwable) {
          throw RuntimeException("Response from $uri with failure: $jsonTree", e)
        }
      },
      fallbackOnThresholdReached = { throw RuntimeException("Couldn't get sorted test classes from Nastradamus") }
    )
  }

  /**
   * Download changeset patches (not to be confused with simple change)
   * Patch - cumulative document (VCS change author + File affected + VCS diff)
   */
  private fun getTeamCityChangesetPatch(): List<String> {
    println("Fetching changesets patches from TeamCity ...")

    val changesets = runBlocking {
      teamCityClient.getChanges().mapConcurrently(maxConcurrency = 5) { change ->
        val modificationId = change.findValue("id").asText()
        val isPersonal = change.findValue("personal")?.asBoolean(false) ?: false
        teamCityClient.downloadChangesPatch(modificationId = modificationId, isPersonal = isPersonal)
      }
    }

    println("Fetching changesets patches completed")

    return changesets
  }

  fun getTeamCityChangesDetails(): List<ChangeEntity> {
    println("Getting changes details from TeamCity ...")

    // across all changes - get their details
    val changeDetails = runBlocking {
      val changes = teamCityClient.getChanges()
      changes.mapConcurrently(maxConcurrency = 5) { change ->
        teamCityClient.getChangeDetails(change.findValue("id").asText())
      }.flatten()
    }

    println("Fetching changes details completed")

    return changeDetails
  }

  fun getBuildInfo(): BuildInfo {
    val triggeredByInfo = teamCityClient.getTriggeredByInfo()

    val triggeredByBuild = triggeredByInfo.findValue("build")
    val aggregatorBuildId: String = if (triggeredByBuild == null) teamCityClient.buildId
    else triggeredByBuild.findValue("id").asText(teamCityClient.buildId)

    val branchName = teamCityClient.getBuildInfo().findValue("branchName")?.asText("") ?: ""

    return BuildInfo(buildId = teamCityClient.buildId,
                     aggregatorBuildId = aggregatorBuildId,
                     branchName = branchName,
                     os = teamCityClient.os,
                     buildType = teamCityClient.buildTypeId)
  }

  fun getRankedClasses(): Map<Class<*>, Int> {
    println("Getting sorted (& bucketed) test classes from Nastradamus ...")

    fun fallback(): Map<Class<*>, Int> {
      var rank = 1
      return unsortedClasses.sortedBy { it.name }.associateWith { rank++ }
    }

    return try {
      withErrorThreshold(
        objName = "NastradamusClient-getRankedClasses",
        errorThreshold = 1,
        action = {
          val changesets = getTeamCityChangesDetails()
          val cases = unsortedClasses.map { TestCaseEntity(it.name) }
          val sortedCases = sendSortingRequest(
            sortRequestEntity = SortRequestEntity(buildInfo = getBuildInfo(), changes = changesets, tests = cases),
            bucketsCount = TestCaseLoader.TEST_RUNNERS_COUNT,
            currentBucketIndex = TestCaseLoader.TEST_RUNNER_INDEX
          )

          var rank = 1
          val rankedTestClassesForCurrentBucket = sortedCases.associate { case -> case.name to rank++ }

          sortedClassesInCurrentBucket = unsortedClasses.filter { it.name in rankedTestClassesForCurrentBucket.keys }.toSet()

          allSortedClasses = unsortedClasses.associateWith { clazz -> rankedTestClassesForCurrentBucket[clazz.name] ?: Int.MAX_VALUE }
          println("Fetching sorted test classes from Nastradamus completed")
          allSortedClasses
        },
        fallbackOnThresholdReached = { fallback() }
      )
    }
    catch (e: Throwable) {
      // fallback in case of any failure (just to get aggregator running)
      System.err.println("Failure during sorting test classes via Nastradamus. Fallback to simple natural sorting.")
      allSortedClasses = fallback()
      allSortedClasses
    }
  }

  fun isClassInBucket(testIdentifier: String, fallbackFunc: (String) -> Boolean): Boolean {
    if (!this::allSortedClasses.isInitialized) getRankedClasses()

    val isMatch: Boolean = withErrorThreshold(
      objName = "NastradamusClient-isClassInBucket",
      errorThreshold = 1,
      action = { sortedClassesInCurrentBucket.any { it.name == testIdentifier } },
      fallbackOnThresholdReached = {
        System.err.println("Couldn't find appropriate bucket for $testIdentifier via Nastradamus")
        fallbackFunc(testIdentifier)
      }
    )

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Nastradamus. Class $testIdentifier matches current bucket ${TestCaseLoader.TEST_RUNNER_INDEX} - $isMatch")
    }

    return isMatch
  }
}