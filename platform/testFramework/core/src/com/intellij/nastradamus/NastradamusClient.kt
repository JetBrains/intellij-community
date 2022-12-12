// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus

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

  private lateinit var sortedClassesCachedResult: Map<Class<*>, Int>

  fun collectTestRunResults(): TestResultRequestEntity {
    val tests = teamCityClient.getTestRunInfo()

    val testResultEntities = tests.map { json ->
      TestResultEntity(
        name = json.findValue("name").asText(),
        status = TestStatus.fromString(json.findValue("status").asText()),
        runOrder = json.findValue("runOrder").asInt(),
        duration = json.findValue("duration")?.asLong() ?: 0,
        buildType = teamCityClient.buildTypeId,
        buildStatusMessage = teamCityClient.getBuildInfo().findValue("statusText").asText()
      )
    }

    return TestResultRequestEntity(testRunResults = testResultEntities)
  }

  fun sendTestRunResults(testResultRequestEntity: TestResultRequestEntity) {
    val uri = URIBuilder(baseUrl.resolve("/result/").normalize())
      .addParameter("build_id", teamCityClient.buildId)
      .build()

    val stringJson = jacksonMapper.writeValueAsString(testResultRequestEntity.testRunResults)

    val httpPost = HttpPost(uri).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    println("Sending test run results to Nastradamus ...")

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $uri with payload $stringJson")
    }

    withErrorThreshold("NastradamusClient") {
      withRetry {
        HttpClient.sendRequest(httpPost) { response ->
          if (response.statusLine.statusCode != 200) {
            if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
              System.err.apply {
                println(response)
                println(response.entity.content.reader().readText())
              }
            }

            throw RuntimeException("Couldn't store test run results on Nastradamus")
          }
        }
      }
    }
  }

  /**
   * Will return tests for this particular bucket
   */
  fun sendSortingRequest(sortRequestEntity: SortRequestEntity): List<TestCaseEntity> {
    val uri = URIBuilder(baseUrl.resolve("/sort/").normalize())
      .addParameter("buckets", TestCaseLoader.TEST_RUNNERS_COUNT.toString())
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

    return withErrorThreshold("NastradamusClient") {
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
          .get(TestCaseLoader.TEST_RUNNER_INDEX.toString())
          .map {
            TestCaseEntity(it.findValue("name").asText())
          }
      }
      catch (e: Throwable) {
        System.err.println("Response from $uri with failure: $jsonTree")
        throw e
      }
    }
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
                     os = teamCityClient.os)
  }

  fun getRankedClasses(): Map<Class<*>, Int> {
    println("Getting sorted (& bucketed) test classes from Nastradamus ...")

    return try {
      withErrorThreshold("NastradamusClient") {
        try {
          val changesets = getTeamCityChangesDetails()
          val cases = unsortedClasses.map { TestCaseEntity(it.name) }
          val sortedCases = sendSortingRequest(SortRequestEntity(buildInfo = getBuildInfo(), changes = changesets, tests = cases))

          var rank = 1
          val ranked = sortedCases.associate { case -> case.name to rank++ }
          sortedClassesCachedResult = unsortedClasses.associateWith { clazz -> ranked[clazz.name] ?: Int.MAX_VALUE }
          println("Fetching sorted test classes from Nastradamus completed")
          sortedClassesCachedResult
        }
        catch (e: Throwable) {
          e.printStackTrace()
          throw e
        }
      }
    }
    catch (e: Throwable) {
      // fallback in case of any failure (just to get aggregator running)
      System.err.println(
        "Failure during sorting test classes via Nastradamus. Fallback to simple natural sorting. For more details take a look at failures above in the log")
      var rank = 1
      unsortedClasses.sortedBy { it.name }.associateWith { rank++ }
    }
  }

  fun isClassInBucket(testIdentifier: String): Boolean {
    if (!this::sortedClassesCachedResult.isInitialized) getRankedClasses()
    val isMatch = sortedClassesCachedResult.keys.any { it.name == testIdentifier }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Nastradamus. Class $testIdentifier matches current bucket ${TestCaseLoader.TEST_RUNNER_INDEX} - $isMatch")
    }

    return isMatch
  }
}