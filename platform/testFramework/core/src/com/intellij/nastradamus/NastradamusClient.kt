// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.TestCaseLoader
import com.intellij.nastradamus.model.*
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.HttpClient
import com.intellij.tool.mapConcurrently
import com.intellij.tool.withRetry
import kotlinx.coroutines.runBlocking
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI

class NastradamusClient(val baseUrl: URI = URI(System.getProperty("idea.nastradamus.url")).normalize()) {

  fun sendSortingRequest(sortRequestEntity: SortRequestEntity): List<TestCaseEntity> {
    val uri = URIBuilder(baseUrl.resolve("/sort/").normalize())
      .addParameter("build_id", TeamCityClient.buildId)
      .addParameter("buckets", TestCaseLoader.TEST_RUNNERS_COUNT.toString())
      .build()

    val stringJson = jacksonObjectMapper().writeValueAsString(sortRequestEntity)

    val httpPost = HttpPost(uri).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    println("Fetching sorted test classes from Nastradamus ...")

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $uri with payload $stringJson")
    }

    val jsonTree = withRetry {
      HttpClient.sendRequest(httpPost) {
        jacksonObjectMapper().readTree(it.entity.content)
      }
    }

    requireNotNull(jsonTree) { "Received data from $uri must not be null" }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Received data from $uri: $jsonTree")
    }

    return jsonTree.fields().asSequence()
      .single { it.key == "sorted_tests" }.value
      .get(TestCaseLoader.TEST_RUNNER_INDEX.toString())
      .map {
        TestCaseEntity(it.findValue("name").asText())
      }
  }

  fun collectTestRunResults(): TestResultRequestEntity {
    val tests = TeamCityClient.getTestRunInfo()

    val testResultEntities = tests.map { json ->
      TestResultEntity(
        name = json.findValue("name").asText(),
        status = TestStatus.fromString(json.findValue("status").asText()),
        runOrder = json.findValue("runOrder").asInt(),
        duration = json.findValue("duration")?.asLong() ?: 0
      )
    }

    return TestResultRequestEntity(testRunResults = testResultEntities)
  }

  fun sendTestRunResults(testResultRequestEntity: TestResultRequestEntity) {
    val uri = URIBuilder(baseUrl.resolve("/result/").normalize())
      .addParameter("build_id", TeamCityClient.buildId)
      .build()

    val stringJson = jacksonObjectMapper().writeValueAsString(testResultRequestEntity.testRunResults)

    val httpPost = HttpPost(uri).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    println("Sending test run results to Nastradamus ...")

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $uri with payload $stringJson")
    }

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

  private fun getTeamCityChangeset(): List<String> = runBlocking {
    println("Fetching changesets patches from TeamCity ...")

    val changesets = TeamCityClient.getChanges().mapConcurrently(maxConcurrency = 5) { change ->
      val modificationId = change.findValue("id").asText()
      val isPersonal = change.findValue("personal")?.asBoolean() ?: false
      TeamCityClient.downloadChangesPatch(modificationId = modificationId, isPersonal = isPersonal)
    }

    println("Fetching changesets patches completed")

    changesets
  }

  fun getRankedClasses(unsortedClasses: List<Class<*>>): Map<Class<*>, Int> {
    println("Getting sorted test classes from Nastradamus ...")

    return try {
      val changesets = getTeamCityChangeset().map { ChangeEntity(it) }
      val cases = unsortedClasses.map { TestCaseEntity(it.name) }
      val sortedCases = sendSortingRequest(SortRequestEntity(changes = changesets, tests = cases))

      var rank = 1
      val ranked = sortedCases.associate { case -> case.name to rank++ }
      val sortedOriginalClasses = unsortedClasses.associateWith { clazz -> ranked[clazz.name] ?: -1 }
      println("Fetching sorted test classes from Nastradamus completed")
      sortedOriginalClasses
    }
    catch (e: Exception) {
      // fallback in case of any failure (just to get aggregator running)
      System.err.println("Failure during sorting test classes via Nastradamus. Fallback to simple reverse sorting")
      System.err.println(e)
      var rank = 1
      unsortedClasses.sortedByDescending { it.name }.associateWith { rank++ }
    }
  }
}