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
    val changes = TeamCityClient.getChanges("219794017")

    val x = runBlocking {
      changes.mapConcurrently(maxConcurrency = 5) { change ->
        val modificationId = change.findValue("id").asText()
        TeamCityClient.downloadChangesPatch(buildTypeId = "bt1921931", modificationId = modificationId)
      }
    }
  }

  @Test
  @Ignore("Do not use TC. Use mocks / test data")
  fun collectingTestResultsFromTC() {
    val tests = TeamCityClient.getTestRunInfo("219794017")

    println(tests)
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
        TestResultEntity(name = "org.jetbrains.xx", status = TestStatus.FAILED),
        TestResultEntity(name = "com.intellij.bxjs", status = TestStatus.SUCCESS),
      )
    )

    client.sendTestRunResults(testRunResult)
  }
}
