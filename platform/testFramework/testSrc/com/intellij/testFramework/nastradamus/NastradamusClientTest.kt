// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.nastradamus

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.nastradamus.NastradamusClient
import com.intellij.nastradamus.model.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.teamcity.TeamCityClient
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.tool.NastradamusCache
import com.intellij.tool.withErrorThreshold
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.rules.TestName
import org.junit.rules.Timeout
import java.net.URI
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText

class NastradamusClientTest {
  init {
    NastradamusCache.eraseCache()
  }

  @JvmField
  @Rule
  val timeoutRule: Timeout = Timeout(30, TimeUnit.SECONDS)

  @JvmField
  @Rule
  val testName: TestName = TestName()

  private val jacksonMapper = jacksonObjectMapper()
  private val tcMockServer = MockWebServer()
  private val nastradamusMockServer = MockWebServer()
  private lateinit var tcClient: TeamCityClient
  private lateinit var nastradamus: NastradamusClient

  private val fakeResponsesDir by lazy {
    Path.of(PlatformTestUtil.getCommunityPath() + "/platform/testFramework/testData/nastradamus")
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
    NastradamusCache.eraseCache()
  }

  private fun setBuildParams(vararg buildProperties: Pair<String, String>): Path {
    val tempPropertiesFile = FileUtil.createTempFile("teamcity_", "_properties_file.properties")

    Properties().apply {
      setProperty("teamcity.build.id", "225659992")
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
                                  buildType = tcClient.buildTypeId,
                                  status = "SUCCESS",
                                  buildStatusMessage = "Tests passed: 15, ignored: 1"),
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
                                  buildType = tcClient.buildTypeId,
                                  status = "UNKNOWN",
                                  buildStatusMessage =
                                  "Canceled (Error while applying patch; cannot find commit 6f53c88397af6fd5cbc32f07159213eff07c1430 in the ssh://git@git.jetbrains.team/intellij.git repository, possible reason: refs/heads/nikita.kudrin/nastradamus branch was updated and the commit sel..."),
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
                                  buildType = tcClient.buildTypeId,
                                  status = "SUCCESS",
                                  buildStatusMessage = "Tests passed: 117"),
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
                            buildType = "ijplatform_master_WorkspaceModelSmokeSmokeTestsCompositeBuild_66",
                            status = "RUNNING"),
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

    val sortedCases = nastradamus.sendSortingRequest(sortRequestEntity = sortEntity,
                                                     bucketsCount = 2,
                                                     currentBucketIndex = 0,
                                                     wasNastradamusDataUsed = true)
    val request = nastradamusMockServer.takeRequest()

    Assert.assertEquals("Requested path should be equal", "/sort/?buckets=2&was_nastradamus_data_used=true", request.path)
    Assert.assertEquals("POST request should be sent", "POST", request.method)

    Assert.assertEquals("Payload is in wrong format", jacksonMapper.writeValueAsString(sortEntity), request.body.readUtf8())
  }

  @Test
  fun successfulErrorThresholdTest() {
    (1..3).forEach {
      val result = withErrorThreshold(
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
        withErrorThreshold(
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

    val result = withErrorThreshold(
      objName = testName.methodName,
      errorThreshold = 3,
      action = { "action" },
      fallbackOnThresholdReached = { "fallback" }
    )

    Assert.assertEquals("Fallback function should be executed in case of threshold limit", "fallback", result)
  }


  private fun sendTestResultTestTemplate(testOccurencesJsonFileName: String): TestResultRequestEntity {
    tcMockServer.enqueue(getOkResponse("teamcity/$testOccurencesJsonFileName"))
    tcMockServer.enqueue(getOkResponse("teamcity/EmptyTestOccurences.json"))
    tcMockServer.enqueue(getOkResponse("teamcity/Build_Info_Triggered_By_Aggregator.json"))
    setFakeResponsesForTeamCityChanges()

    nastradamusMockServer.enqueue(MockResponse()
                                    .setBody("")
                                    .addHeader("Content-Type", "application/json"))

    val testResultRequestEntity = nastradamus.collectTestRunResults()
    nastradamus.sendTestRunResults(testResultRequestEntity, wasNastradamusDataUsed = true)

    val request = nastradamusMockServer.takeRequest()

    Assert.assertEquals("POST request should be sent", "POST", request.method)
    Assert.assertEquals("Requested path should be equal", "/result/?was_nastradamus_data_used=true", request.path)

    Assert.assertTrue("""
      Bucket id and total bucket number should not be 0.
      ${testResultRequestEntity.testRunResults}
      """.trimIndent(),
                      testResultRequestEntity.testRunResults.all { it.bucketId != 0 && it.bucketsNumber != 0 })

    return testResultRequestEntity
  }

  @Test
  fun sendTestRunResultToNostradamus_CommunityTests() {
    val testResultRequestEntity = sendTestResultTestTemplate("TestOccurences_Build_296805903.json")

    Assert.assertTrue("""
      Converted test entities must have 5 classes.
      ${testResultRequestEntity.testRunResults}
      """.trimIndent(), testResultRequestEntity.testRunResults.size == 5)

    val productModulesClassResult = testResultRequestEntity.testRunResults
      .single { it.fullName == "com.intellij.platform.runtime.repository.serialization.ProductModulesLoaderTest" }

    Assert.assertTrue("""
      ProductModulesLoaderTest class must have 1 test and be with correct duration and bucket attributes.
      $productModulesClassResult
      """.trimIndent(),
                      productModulesClassResult.testResults.size == 1 &&
                      productModulesClassResult.bucketId == 4
                      && productModulesClassResult.bucketsNumber == 8
                      && productModulesClassResult.durationMs == 446L
    )

    val xxHash3TestClassResult = testResultRequestEntity.testRunResults
      .single { it.fullName == "com.intellij.util.lang.XxHash3Test" }

    Assert.assertTrue("""
      XxHash3Test class must have 184 tests and be with correct duration and bucket attributes.
      $xxHash3TestClassResult
      """.trimIndent(),
                      xxHash3TestClassResult.testResults.size == 184 &&
                      xxHash3TestClassResult.bucketId == 4
                      && xxHash3TestClassResult.bucketsNumber == 8
                      && xxHash3TestClassResult.durationMs == 158L
    )
  }

  @Test
  fun sendTestRunResultToNostradamus_ParametrizedTests() {
    val testResultRequestEntity = sendTestResultTestTemplate("TestOccurences_Build_300204446.json")

    Assert.assertTrue("""
      Converted test entities must have 8 classes.
      ${testResultRequestEntity.testRunResults}
      """.trimIndent(), testResultRequestEntity.testRunResults.size == 8)

    val stringExtensionClassResult = testResultRequestEntity.testRunResults
      .single { it.fullName == "com.intellij.workspaceModel.integrationTests.tests.plugin.StringExtensionTest" }

    Assert.assertTrue("""
      StringExtensionTest class must have 4 test and be with correct duration and bucket attributes.
      $stringExtensionClassResult
      """.trimIndent(),
                      stringExtensionClassResult.testResults.size == 4 &&
                      stringExtensionClassResult.bucketId == 4
                      && stringExtensionClassResult.bucketsNumber == 8
                      && stringExtensionClassResult.durationMs == 131L
    )

  }
}
