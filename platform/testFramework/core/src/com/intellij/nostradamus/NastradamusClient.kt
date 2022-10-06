// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nostradamus

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.TestCaseLoader
import com.intellij.nostradamus.model.ChangeEntity
import com.intellij.nostradamus.model.SortRequestEntity
import com.intellij.nostradamus.model.TestCaseEntity
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.HttpClient
import com.intellij.tool.mapConcurrently
import com.intellij.tool.withRetry
import kotlinx.coroutines.runBlocking
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI

class NastradamusClient(val baseUrl: URI = URI(System.getProperty("idea.nostradamus.url")).normalize()) {

  fun sendSortingRequest(sortRequestEntity: SortRequestEntity): List<TestCaseEntity> {
    val url = baseUrl.resolve("/sort")
    val stringJson = jacksonObjectMapper().writeValueAsString(sortRequestEntity)

    val httpPost = HttpPost(url).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      entity = StringEntity(stringJson, ContentType.APPLICATION_JSON)
    }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Requesting $url with payload $stringJson")
    }

    val jsonTree = withRetry {
      HttpClient.sendRequest(httpPost) {
        jacksonObjectMapper().readTree(it.entity.content)
      }
    }

    requireNotNull(jsonTree) { "Received data from $url must not be null" }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Received data from $url: $jsonTree")
    }

    return jsonTree.fields().asSequence().single { it.key == "sorted_tests" }.value.map {
      TestCaseEntity(it.findValue("name").asText())
    }
  }

  private fun getTeamCityChangeset(): List<String> = runBlocking {
    TeamCityClient.getChanges().mapConcurrently(maxConcurrency = 10) { change ->
      val modificationId = change.findValue("id").asText()
      TeamCityClient.downloadChangesPatch(modificationId)
    }
  }

  fun getRankedClasses(unsortedClasses: List<Class<*>>): Map<Class<*>, Int> {
    return try {
      val changesets = getTeamCityChangeset().map { ChangeEntity(it) }
      val cases = unsortedClasses.map { TestCaseEntity(it.name) }
      val sortedCases = sendSortingRequest(SortRequestEntity(changes = changesets, tests = cases))

      var rank = 1
      val ranked = sortedCases.associate { case -> case.name to rank++ }
      unsortedClasses.associateWith { clazz -> ranked[clazz.name] ?: -1 }
    }
    catch (e: Exception) {
      // fallback in case of any failure (just to get aggregator running)
      System.err.println("Failure during sorting test classes via Nastradamus. Fallback to simple shuffle sorting")
      var rank = 1
      unsortedClasses.shuffled().associateWith { rank++ }
    }
  }
}