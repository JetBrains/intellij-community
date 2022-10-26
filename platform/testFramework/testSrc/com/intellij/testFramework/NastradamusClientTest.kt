// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.nastradamus.NastradamusClient
import com.intellij.nastradamus.model.*
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.mapConcurrently
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import java.net.URI

class NastradamusClientTest {
  @Test
  @Ignore("Do not use TC. Use mocks / test data")
  fun debugAuthOnTC() {
    val uri = URI(
      "https://buildserver.labs.intellij.net/app/rest/buildTypes/id:ijplatform_master_IdeaSmokeTestsCompositeBuild_2_Nastradamus"
    )
    TeamCityClient.get(uri)
  }

  @Test
  @Ignore("Do not use TC. Use mocks / test data")
  fun collectingChangesetOnTC() {
    val changes = TeamCityClient.getChanges("225659992").take(3)

    runBlocking {
      changes.mapConcurrently(maxConcurrency = 2) { change ->
        val modificationId = change.findValue("id").asText()

        val isPersonal = change.findValue("personal")?.asBoolean() ?: false
        TeamCityClient.downloadChangesPatch(buildTypeId = "ijplatform_master_IdeaSmokeTestsNostradamus",
                                            modificationId = modificationId,
                                            isPersonal = isPersonal)
      }
    }

    runBlocking {
      changes.mapConcurrently(maxConcurrency = 2) { change ->
        val modificationId = change.findValue("id").asText()
        val isPersonal = change.findValue("personal")?.asBoolean() ?: false
        TeamCityClient.downloadChangesPatch(buildTypeId = "ijplatform_master_IdeaSmokeTestsNostradamus",
                                            modificationId = modificationId,
                                            isPersonal = isPersonal)
      }
    }
  }

  @Test
  @Ignore("Do not use TC. Use mocks / test data")
  fun collectingTestResultsFromTC() {
    val tests = TeamCityClient.getTestRunInfo("226830449")

    val testResultEntities = tests.map { json ->
      TestResultEntity(
        name = json.findValue("name").asText(),
        status = TestStatus.fromString(json.findValue("status").asText()),
        runOrder = json.findValue("runOrder").asInt(),
        duration = json.findValue("duration")?.asLong() ?: 0
      )
    }

    println(testResultEntities)
  }

  @Test
  @Ignore("Do not use dedicated instance. Use mocks / spin up a new server")
  fun sendSortingDataToNostradamus() {
    val client = NastradamusClient(URI("http://127.0.0.1:8000/").normalize())

    val sortEntity = SortRequestEntity(
      changes = listOf(ChangeEntity("some data")),
      tests = listOf(TestCaseEntity("org.jetbrains.xx"), TestCaseEntity("com.intellij.bxjs"))
    )

    val sortedCases = client.sendSortingRequest(sortEntity)
    println(sortedCases)
  }

  @Test
  @Ignore("Do not use dedicated instance. Use mocks / spin up a new server")
  fun sendTestRunResultToNostradamus() {
    val client = NastradamusClient(URI("http://127.0.0.1:8000/").normalize())

    val testRunResult = TestResultRequestEntity(
      testRunResults = listOf(
        TestResultEntity(name = "org.jetbrains.xx", status = TestStatus.FAILED, runOrder = -1, duration = 10),
        TestResultEntity(name = "com.intellij.bxjs", status = TestStatus.SUCCESS, runOrder = 10, duration = 0),
      )
    )

    client.sendTestRunResults(testRunResult)
  }
}
