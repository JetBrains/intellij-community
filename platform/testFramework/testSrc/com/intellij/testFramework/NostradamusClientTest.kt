// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.nostradamus.NostradamusClient
import com.intellij.nostradamus.model.ChangeEntity
import com.intellij.nostradamus.model.SortRequestEntity
import com.intellij.nostradamus.model.TestCaseEntity
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.mapConcurrently
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import java.net.URI

class NostradamusClientTest {
  @Test
  @Ignore("Do not use TC. Use mocks / test data")
  fun debugAuthOnTC() {
    val uri = URI(
      "https://buildserver.labs.intellij.net/app/rest/buildTypes/id:ijplatform_master_IdeaSmokeTestsCompositeBuild_2_Nostradamus"
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
  @Ignore("Do not use dedicated instance. Use mocks / spin up a new server")
  fun sendDataToNostradamus() {
    val client = NostradamusClient(URI("http://127.0.0.1:8000/sort/").normalize())

    val sortEntity = SortRequestEntity(
      changes = listOf(ChangeEntity("some data")),
      tests = listOf(TestCaseEntity("org.jetbrains.xx"), TestCaseEntity("com.intellij.bxjs"))
    )

    val sortedCases = client.sendSortingRequest(sortEntity)
    println(sortedCases)
  }
}
