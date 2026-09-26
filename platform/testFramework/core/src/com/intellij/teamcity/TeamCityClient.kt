// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.teamcity

import com.intellij.TestCaseLoader
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.openapi.application.PathManager
import com.intellij.tool.HttpClient
import com.intellij.tool.withErrorThreshold
import com.intellij.tool.withRetry
import org.apache.http.HttpRequest
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/** Clone of the `com.intellij.ide.starter.ci.teamcity.TeamCityClient` */
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
        ps.forEach { (k, v) ->
          if (k != null && v != null) {
            map[k.toString()] = v.toString()
          }
        }
        map
      }
    }
    catch (_: Throwable) {
      emptyMap()
    }

  private val systemProperties by lazy {
    loadProperties(systemPropertiesFilePath)
      .plus(System.getProperties().map { it.key.toString() to it.value.toString() })
  }

  private val restUri = baseUri.resolve("/app/rest/")

  val buildNumber: String by lazy { System.getenv("BUILD_NUMBER") ?: "" }

  val configurationName: String? by lazy { systemProperties["teamcity.buildConfName"] }

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
  val branchName: String by lazy { systemProperties.plus(buildParams)["teamcity.build.branch"] ?: "" }

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
      TeamCityReporter.reportPublishArtifacts(spec)
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

    @OptIn(ExperimentalPathApi::class)
    artifactDir.deleteRecursively()
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
