// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.nastradamus.NastradamusClient
import com.intellij.nastradamus.model.*
import com.intellij.teamcity.TeamCityClient
import com.intellij.tool.Cache
import com.intellij.tool.withErrorThreshold
import com.intellij.util.io.readText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class NastradamusClientTest {
  init {
    Cache.eraseCache()
  }

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
    return TeamCityClient(baseUri = URI("http://localhost:${tcMockServer.port}").normalize(),
                          systemPropertiesFilePath = setBuildParams(*buildProperties))
  }

  private fun getNastradamusClientWithMock(unsortedClasses: List<Class<*>> = listOf(), teamCityClient: TeamCityClient): NastradamusClient {
    return NastradamusClient(
      baseUrl = URI("http://localhost:${nastradamusMockServer.port}").normalize(),
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
  @Ignore("Do not use TC. Use mocks / test data")
  fun collectingTestResultsFromTC() {
    val tests = tcClient.getTestRunInfo("226830449")

    val testResultEntities = tests.map { json ->
      TestResultEntity(
        name = json.findValue("name").asText(),
        status = TestStatus.fromString(json.findValue("status").asText()),
        runOrder = json.findValue("runOrder").asInt(),
        duration = json.findValue("duration")?.asLong() ?: 0,
        buildType = tcClient.buildTypeId,
        buildStatusMessage = tcClient.getBuildInfo().findValue("statusText").asText()
      )
    }

    println(testResultEntities)
  }

  @Test
  fun getBuildInfoTriggeredByAggregator() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_By_Aggregator.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = "239754106",
                                  branchName = "nikita.kudrin/nastradamus",
                                  os = "Linux"),
                        buildInfo)
  }

  @Test
  fun getBuildInfoTriggeredManually() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_Manually.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = tcClient.buildId,
                                  branchName = "nikita.kudrin/nastradamus",
                                  os = "Linux"),
                        buildInfo)
  }

  @Test
  fun getBuildInfoTriggeredByRemoteRun() {
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_With_Remote_Run.json"))

    val buildInfo = nastradamus.getBuildInfo()
    Assert.assertEquals(BuildInfo(buildId = tcClient.buildId,
                                  aggregatorBuildId = tcClient.buildId,
                                  branchName = "master",
                                  os = "Linux"),
                        buildInfo)
  }

  @Test
  fun sendSortingDataToNostradamus() {
    val jacksonMapper = jacksonObjectMapper()

    val testCases = listOf(TestCaseEntity("org.jetbrains.xx"), TestCaseEntity("com.intellij.bxjs"))

    val sortEntity = SortRequestEntity(
      buildInfo = BuildInfo(buildId = tcClient.buildId,
                            aggregatorBuildId = "23232323",
                            branchName = "refs/head/strange_branch",
                            os = "linux"),
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

  @Test(expected = RuntimeException::class)
  fun errorThresholdTest() {

    (1..3).forEach {
      try {
        withErrorThreshold("TestErrorThreshold", errorThreshold = 3) {
          throw Exception("Badums")
        }
      }
      catch (e: Throwable) {
      }
    }

    withErrorThreshold("TestErrorThreshold", errorThreshold = 3) { }
  }


  @Test
  @Ignore("Do not use dedicated instance. Use mocks / spin up a new server")
  fun sendTestRunResultToNostradamus() {
    val client = NastradamusClient(URI("http://127.0.0.1:8000/").normalize(), unsortedClasses = listOf())

    val testRunResult = TestResultRequestEntity(
      testRunResults = listOf(
        TestResultEntity(
          name = "org.jetbrains.xx",
          status = TestStatus.FAILED,
          runOrder = -1,
          duration = 10,
          buildType = "build_type_x",
          buildStatusMessage = "okay"
        ),
        TestResultEntity(
          name = "com.intellij.bxjs",
          status = TestStatus.SUCCESS,
          runOrder = 10,
          duration = 0,
          buildType = "new_build_type",
          buildStatusMessage = ""
        ),
      )
    )

    client.sendTestRunResults(testRunResult)
  }
}
