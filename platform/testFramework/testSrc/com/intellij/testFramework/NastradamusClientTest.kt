// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.nastradamus.NastradamusClient
import com.intellij.nastradamus.model.BuildInfo
import com.intellij.nastradamus.model.ChangeEntity
import com.intellij.nastradamus.model.SortRequestEntity
import com.intellij.nastradamus.model.TestCaseEntity
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.Cache
import com.intellij.tool.withErrorThreshold
import com.intellij.util.io.readText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.rules.TestName
import org.junit.rules.Timeout
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NastradamusClientTest {
  init {
    Cache.eraseCache()
  }

  @JvmField
  @Rule
  val timeoutRule = Timeout(30, TimeUnit.SECONDS)

  @JvmField
  @Rule
  val testName: TestName = TestName()

  private val jacksonMapper = jacksonObjectMapper()
  private val tcMockServer = MockWebServer()
  private val nastradamusMockServer = MockWebServer()
  private lateinit var tcClient: TeamCityClient
  private lateinit var nastradamus: NastradamusClient

  private val fakeResponsesDir by lazy {
    Paths.get(this::class.java.classLoader.getResource("nastradamus")!!.toURI())
  }

  @Before
  fun beforeEach() {
    tcMockServer.start()
    tcClient = getTeamCityClientWithMock()
    nastradamus = getNastradamusClientWithMock(teamCityClient = tcClient)
  }

  @After
  fun afterEach() {
    tcMockServer.shutdown()
    Cache.eraseCache()
  }

  private fun setBuildParams(vararg buildProperties: Pair<String, String>): Path {
    val tempPropertiesFile = File.createTempFile("teamcity_", "_properties_file.properties")

    Properties().apply {
      setProperty("teamcity.build.id", "100500")
      setProperty("teamcity.buildType.id", "bt3989238923")
      setProperty("teamcity.auth.userId", "fake_user_id")
      setProperty("teamcity.auth.password", "fake_password")
      setProperty("teamcity.agent.jvm.os.name", "Linux")

      buildProperties.forEach { this.setProperty(it.first, it.second) }

      store(tempPropertiesFile.outputStream(), "")
    }

    return tempPropertiesFile.toPath()
  }

  private fun getTeamCityClientWithMock(vararg buildProperties: Pair<String, String>): TeamCityClient {
    return TeamCityClient(baseUri = URI("http://${tcMockServer.hostName}:${tcMockServer.port}").normalize(),
                          systemPropertiesFilePath = setBuildParams(*buildProperties))
  }

  private fun getNastradamusClientWithMock(unsortedClasses: List<Class<*>> = listOf(), teamCityClient: TeamCityClient): NastradamusClient {
    return NastradamusClient(
      baseUrl = URI("http://${nastradamusMockServer.hostName}:${nastradamusMockServer.port}").normalize(),
      unsortedClasses = listOf(),
      teamCityClient = teamCityClient
    )
  }

  private fun getOkResponse(fakeRelativePath: String) = MockResponse()
    .setBody(fakeResponsesDir.resolve(fakeRelativePath).readText())
    .addHeader("Content-Type", "application/json")


  private fun setFakeResponsesForTeamCityChanges() {
    tcClient = getTeamCityClientWithMock(Pair("teamcity.build.id", "225659992"))

    tcMockServer.enqueue(getOkResponse("teamcity/Changes.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/Change_Personal_789287.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/Change_85867377.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/Change_85866796.json"))
  }

  @Test
  fun collectingChangesetOnTC() {
    setFakeResponsesForTeamCityChanges()
    nastradamus = getNastradamusClientWithMock(teamCityClient = tcClient)
    val changesEntities = nastradamus.getTeamCityChangesDetails()

    Assert.assertEquals("Unexpected count of change entities", 5, changesEntities.size)
    changesEntities.forEach { Assert.assertEquals("Change type should be edited", "edited", it.changeType) }
    changesEntities.forEach { Assert.assertTrue("Change type should be edited", it.comment.isNotBlank()) }
    changesEntities.forEach { Assert.assertTrue("File path should not be blank", it.filePath.isNotBlank()) }
    changesEntities.forEach { Assert.assertTrue("Relative file path should not be blank", it.relativeFile.isNotBlank()) }
    changesEntities.forEach { Assert.assertTrue("User name must not be blank", it.userName.isNotBlank()) }
  }

  @Test
  fun getBuildInfoTriggeredByAggregator() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_By_Aggregator.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = "239754106",
                                  branchName = "nikita.kudrin/nastradamus",
                                  os = "Linux",
                                  buildType = tcClient.buildTypeId),
                        buildInfo)
  }

  @Test
  fun getBuildInfoTriggeredManually() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_Manually.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = tcClient.buildId,
                                  branchName = "nikita.kudrin/nastradamus",
                                  os = "Linux",
                                  buildType = tcClient.buildTypeId),
                        buildInfo)
  }

  @Test
  fun getBuildInfoTriggeredByRemoteRun() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_With_Remote_Run.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = tcClient.buildId,
                                  branchName = "master",
                                  os = "Linux",
                                  buildType = tcClient.buildTypeId),
                        buildInfo)
  }

  @Test
  fun sendSortingDataToNostradamus() {
    val testCases = listOf(TestCaseEntity("org.jetbrains.xx"),
                           TestCaseEntity("com.intellij.bxjs"),
                           TestCaseEntity("org.m.d.Clazzz"))

    val sortEntity = SortRequestEntity(
      buildInfo = BuildInfo(buildId = tcClient.buildId,
                            aggregatorBuildId = "23232323",
                            branchName = "refs/head/strange_branch",
                            os = "linux",
                            buildType = "ijplatform_master_WorkspaceModelSmokeSmokeTestsCompositeBuild_66"),
      changes = listOf(ChangeEntity(filePath = "file/path/file.xx",
                                    relativeFile = "relative/path",
                                    beforeRevision = "00230203",
                                    afterRevision = "2322323",
                                    changeType = "edited",
                                    comment = "some comment",
                                    userName = "user.name@x.com",
                                    date = "2022")),
      tests = testCases
    )

    val response = jacksonMapper.writeValueAsString(mapOf("sorted_tests" to mapOf(0 to testCases)))

    nastradamusMockServer.enqueue(MockResponse()
                                    .setBody(response)
                                    .addHeader("Content-Type", "application/json"))

    val sortedCases = nastradamus.sendSortingRequest(sortRequestEntity = sortEntity, bucketsCount = 2, currentBucketIndex = 0)
    val request = nastradamusMockServer.takeRequest()

    Assert.assertEquals("Requested path should be equal", "/sort/?buckets=2", request.path)
    Assert.assertEquals("POST request should be sent", "POST", request.method)

    Assert.assertEquals("Payload is in wrong format", jacksonMapper.writeValueAsString(sortEntity), request.body.readUtf8())
  }

  @Test
  fun successfulErrorThresholdTest() {
    (1..3).forEach {
      val result = withErrorThreshold<String>(
        objName = testName.methodName,
        errorThreshold = 1,
        action = { it.toString() },
        fallbackOnThresholdReached = { "fallback" }
      )

      Assert.assertEquals(it.toString(), result)
    }
  }

  @Test
  fun negativeErrorThresholdTest() {
    val counter = AtomicInteger()

    (1..3).forEach {
      try {
        withErrorThreshold<String>(
          objName = testName.methodName,
          errorThreshold = 3,
          action = { throw Exception("Badums") },
          fallbackOnThresholdReached = { "fallback" }
        )
      }
      catch (e: Exception) {
        counter.incrementAndGet()
      }
    }

    Assert.assertEquals("Count of failures should be 3", 3, counter.get())

    val result = withErrorThreshold<String>(
      objName = testName.methodName,
      errorThreshold = 3,
      action = { "action" },
      fallbackOnThresholdReached = { "fallback" }
    )

    Assert.assertEquals("Fallback function should be executed in case of threshold limit", "fallback", result)
  }

  @Test
  fun sendTestRunResultToNostradamus() {
    tcMockServer.enqueue(getOkResponse("teamcity/TestOccurences.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/EmptyTestOccurences.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_By_Aggregator.json"))

    nastradamusMockServer.enqueue(MockResponse()
                                    .setBody("")
                                    .addHeader("Content-Type", "application/json"))

    val testResultRequestEntity = nastradamus.collectTestRunResults()
    nastradamus.sendTestRunResults(testResultRequestEntity)

    val request = nastradamusMockServer.takeRequest()

    Assert.assertEquals("POST request should be sent", "POST", request.method)

    Assert.assertTrue("""
      Converted test entities must have 2 muted tests.
      ${testResultRequestEntity.testRunResults}
      """.trimIndent(), testResultRequestEntity.testRunResults.count { it.isMuted } == 2)

    Assert.assertTrue("""
      Bucket id and total bucket number should not be 0.
      ${testResultRequestEntity.testRunResults}
      """.trimIndent(),
                      testResultRequestEntity.testRunResults.all { it.bucketId != 0 && it.bucketsNumber != 0 })
  }
}
