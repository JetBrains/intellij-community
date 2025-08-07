// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.teamcity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.TestCaseLoader
import com.intellij.nastradamus.model.ChangeEntity
import com.intellij.openapi.application.PathManager
import com.intellij.tool.HttpClient
import com.intellij.tool.NastradamusCache
import com.intellij.tool.withErrorThreshold
import com.intellij.tool.withRetry
import org.apache.http.HttpRequest
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

/** Clone of the com.intellij.ide.starter.ci.teamcity.TeamCityClient */
class TeamCityClient(
  val baseUri: URI = URI("https://buildserver.labs.intellij.net").normalize(),
  private val systemPropertiesFilePath: Path = Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
) {
  // temporary directory, where artifact will be moved for preparation for publishing
  val artifactForPublishingDir: Path by lazy {
    PathManager.getLogDir().parent.resolve("teamcity-artifacts-for-publish").createDirectories()
  }

  private fun loadProperties(propertiesPath: Path) =
    try {
      propertiesPath.bufferedReader().use {
        val map = mutableMapOf<String, String>()
        val ps = Properties()
        ps.load(it)

        ps.forEach { k, v ->
          if (k != null && v != null) {
            map[k.toString()] = v.toString()
          }
        }
        map
      }
    }
    catch (t: Throwable) {
      emptyMap()
    }

  private val systemProperties by lazy {
    loadProperties(systemPropertiesFilePath)
      .plus(System.getProperties().map { it.key.toString() to it.value.toString() })
  }

  private val restUri = baseUri.resolve("/app/rest/")
  val guestAuthUri = baseUri.resolve("/guestAuth/app/rest/")

  val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }

  val configurationName by lazy { systemProperties["teamcity.buildConfName"] }

  private val buildParams by lazy {
    val configurationPropertiesFile = systemProperties["teamcity.configuration.properties.file"]

    if (configurationPropertiesFile.isNullOrBlank()) return@lazy emptyMap()
    loadProperties(Path(configurationPropertiesFile))
  }

  private fun getExistingParameter(name: String, impreciseNameMatch: Boolean = false): String {
    val totalParams = systemProperties.plus(buildParams)

    val paramValue = if (impreciseNameMatch) {
      val paramCandidates = totalParams.filter { it.key.contains(name) }
      if (paramCandidates.size > 1) System.err.println("Found many parameters matching $name. Candidates: $paramCandidates")
      paramCandidates[paramCandidates.toSortedMap().firstKey()]
    }
    else totalParams[name]

    return paramValue ?: error("Parameter $name is not specified in the build!")
  }

  val buildId: String by lazy { getExistingParameter("teamcity.build.id") }
  val buildTypeId: String by lazy { getExistingParameter("teamcity.buildType.id") }
  val os: String by lazy { getExistingParameter("teamcity.agent.jvm.os.name") }
  val branchName by lazy { systemProperties.plus(buildParams)["teamcity.build.branch"] ?: "" }
  val isPersonalBuild by lazy {
    systemProperties.plus(buildParams)["teamcity.build.branch"].equals("true", ignoreCase = true)
  }

  private val userName: String by lazy { getExistingParameter("teamcity.auth.userId") }
  private val password: String by lazy { getExistingParameter("teamcity.auth.password") }

  private fun <T : HttpRequest> T.withAuth(): T = this.apply {
    addHeader(BasicScheme().authenticate(UsernamePasswordCredentials(userName, password), this, null))
  }

  private val jacksonMapper: ObjectMapper = jacksonObjectMapper()

  fun get(fullUrl: URI): JsonNode {
    val request = HttpGet(fullUrl).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      withAuth()
    }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Request to TeamCity: $fullUrl")
    }

    val result =
      withErrorThreshold(
        objName = "TeamCityClient-get",
        action = {
          withRetry {
            HttpClient.sendRequest(request = request) {
              if (it.statusLine.statusCode != 200) {
                throw RuntimeException(
                  """
                TeamCity returned not successful status code ${it.statusLine.statusCode}.
                ${InputStreamReader(it.entity.content).readText()}
                """.trimIndent())
              }

              jacksonMapper.readTree(it.entity.content)
            }
          }
        },
        fallbackOnThresholdReached = { throw RuntimeException("Couldn't get data from TeamCity $fullUrl") }
      )

    return requireNotNull(result) { "Request ${request.uri} failed" }
  }

  private fun downloadChangesPatch(buildTypeId: String, modificationId: String, isPersonal: Boolean): String {
    val uri = baseUri.resolve("/downloadPatch.html?buildTypeId=${buildTypeId}&modId=${modificationId}&personal=$isPersonal")

    return NastradamusCache.get(uri) {
      val outputStream = ByteArrayOutputStream()

      if (!HttpClient.download(request = HttpGet(uri).withAuth(), outStream = outputStream, retries = 3)) {
        throw RuntimeException("Couldn't download patch $uri in 3 attempts")
      }

      outputStream.toString("UTF-8")
    }
  }

  fun downloadChangesPatch(modificationId: String, isPersonal: Boolean): String =
    downloadChangesPatch(buildTypeId = buildTypeId,
                         modificationId = modificationId,
                         isPersonal = isPersonal)

  fun getChanges(buildId: String): List<JsonNode> {
    val fullUrl = restUri.resolve("changes?locator=build:(id:$buildId)")

    val rawData = NastradamusCache.get(fullUrl) { get(fullUrl).toString() }

    return jacksonMapper.readTree(rawData).fields().asSequence()
      .filter { it.key == "change" }
      .flatMap { it.value }
      .toList()
  }

  fun getChanges() = getChanges(buildId)

  fun getChangeDetails(changeId: String): List<ChangeEntity> {
    val fullUrl = restUri.resolve("changes/id:$changeId")

    fun processData(jsonRoot: JsonNode): List<ChangeEntity> {
      val comment = jsonRoot.findValue("comment").asText()
      val userName = jsonRoot.findValue("username").asText()
      val date = jsonRoot.findValue("date").asText()

      val filesFields = jsonRoot.findValue("files")
        .findValue("file")
        .toList()

      return filesFields.map { fileField ->
        ChangeEntity(
          filePath = fileField.findValue("file").asText(),
          relativeFile = fileField.findValue("relative-file").asText(),
          beforeRevision = fileField.findValue("before-revision")?.asText("") ?: "",
          afterRevision = fileField.findValue("after-revision")?.asText("") ?: "",
          changeType = fileField.findValue("changeType").asText(),
          comment = comment,
          userName = userName,
          date = date
        )
      }
    }

    val rawChange = NastradamusCache.get(fullUrl) { get(fullUrl).toString() }
    return processData(jacksonMapper.readTree(rawChange))
  }

  private fun getTestRunInfo(buildId: String): List<JsonNode> {
    val countOfTestsOnPage = 200
    var startPosition = 0
    val accumulatedTests: MutableList<JsonNode> = mutableListOf()

    var currentTests: List<JsonNode>

    println("Getting test run info from TC ...")

    do {
      val fullUrl = restUri
        .resolve("testOccurrences?locator=build:(id:$buildId),ignored:any,muted:any,count:$countOfTestsOnPage,start:$startPosition," +
                 "includePersonal:true&fields=nextHref," +
                 "testOccurrence(id,name,status,duration,currentlyInvestigated,currentlyMuted,muted,test(id,parsedTestName),newFailure,metadata(count),nextFixed(id),runOrder)")

      val rawData = NastradamusCache.get(fullUrl) { get(fullUrl).toString() }

      currentTests = jacksonMapper.readTree(rawData).fields().asSequence()
        .filter { it.key == "testOccurrence" }
        .flatMap { it.value }
        .toList()

      accumulatedTests.addAll(currentTests)

      startPosition += countOfTestsOnPage
    }
    while (currentTests.isNotEmpty())

    println("Test run info acquired. Count of tests ${accumulatedTests.size}")

    return accumulatedTests
  }

  fun getTestRunInfo(): List<JsonNode> = getTestRunInfo(buildId)

  private fun getBuildInfo(buildId: String): JsonNode {
    val fullUrl = restUri.resolve("builds/$buildId")

    val rawData = NastradamusCache.get(fullUrl) { get(fullUrl).toString() }
    return jacksonMapper.readTree(rawData)
  }

  fun getBuildInfo(): JsonNode = getBuildInfo(buildId)

  private fun getTriggeredByInfo(buildId: String): JsonNode = getBuildInfo(buildId).findValue("triggered")

  fun getTriggeredByInfo(): JsonNode = getTriggeredByInfo(buildId)

  /**
   * [source] - source path of artifact
   * [artifactPath] - new path (relative, where artifact will be present)
   * [artifactName] - name of artifact
   */
  fun publishTeamCityArtifacts(
    source: Path,
    artifactPath: String,
    artifactName: String = source.fileName.toString(),
    zipContent: Boolean = true,
  ) {
    if (!source.exists()) {
      System.err.println("TeamCity artifact $source does not exist")
      return
    }

    fun printTcArtifactsPublishMessage(spec: String) {
      // https://www.jetbrains.com/help/teamcity/2025.07/configuring-general-settings.html#Artifact+Paths
      // > You can specify exact file paths or patterns, one per line or comma-separated.
      // Because of that feature, files and directories with a comma in the name can't be mentioned as is.
      // So, commas are replaced with wildcards.
      // See also TW-19333.
      val spec = spec.replace(",", "*")
      println(" ##teamcity[publishArtifacts '$spec'] ")
    }

    var suffix: String
    var nextSuffix = 0
    var artifactDir: Path
    do {
      suffix = if (nextSuffix == 0) "" else "-$nextSuffix"
      artifactDir = (artifactForPublishingDir / artifactPath / (artifactName + suffix)).normalize().toAbsolutePath()
      nextSuffix++
    }
    while (artifactDir.exists())

    artifactDir.toFile().deleteRecursively()
    artifactDir.createDirectories()

    if (source.isDirectory()) {
      Files.walk(source).use { files ->
        for (path in files) {
          path.copyTo(target = artifactDir.resolve(source.relativize(path)), overwrite = true)
        }
      }
      if (zipContent) {
        printTcArtifactsPublishMessage("${artifactDir.toRealPath()}/** => $artifactPath/$artifactName$suffix.zip")
      }
      else {
        printTcArtifactsPublishMessage("${artifactDir.toRealPath()}/** => $artifactPath$suffix")
      }
    }
    else {
      val tempFile = artifactDir
      source.copyTo(tempFile, overwrite = true)
      if (zipContent) {
        printTcArtifactsPublishMessage("${tempFile.toRealPath()} => $artifactPath/${artifactName + suffix}.zip")
      }
      else {
        printTcArtifactsPublishMessage("${tempFile.toRealPath()} => $artifactPath")
      }
    }
  }
}

