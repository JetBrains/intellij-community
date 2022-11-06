package com.intellij.teamcity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.TestCaseLoader
import com.intellij.tool.HttpClient
import com.intellij.tool.withRetry
import org.apache.http.HttpRequest
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*


object TeamCityClient {
  private val downloadedDataCache: MutableMap<String, String> = mutableMapOf()
  private val cacheDir = Paths.get("TeamCityClientCache").apply {
    createDirectories()
    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED)
      println("Init TeamCityClient cache dir ${this.toRealPath()}")
  }.toRealPath()

  private fun putDataToCache(uri: URI, value: String) {
    val hash = uri.toString().hashCode().toString()
    putDataToCache(key = hash, value = value)
  }

  private fun putDataToCache(key: String, value: String) {
    downloadedDataCache[key] = value
    cacheDir.resolve(key).apply {
      createFile()
      writeText(value)
    }
  }

  // try to initialize cache from cache directory
  private fun initCache() {
    if (downloadedDataCache.isEmpty()) {
      cacheDir.listDirectoryEntries().forEach { filePath ->
        downloadedDataCache[filePath.nameWithoutExtension] = filePath.readText()
      }
    }
  }

  private fun getDataFromCache(uri: URI): String? {
    return getDataFromCache(uri.toString().hashCode().toString())
  }

  private fun getDataFromCache(key: String): String? {
    initCache()

    return downloadedDataCache[key]
  }

  private fun loadProperties(file: String?) =
    try {
      File(file ?: throw Error("No file!")).bufferedReader().use {
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
    loadProperties(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
      .plus(System.getProperties().map { it.key.toString() to it.value.toString() })
  }

  private val baseUri = URI("https://buildserver.labs.intellij.net").normalize()

  private val restUri = baseUri.resolve("/app/rest/")
  private val guestAuthUri = baseUri.resolve("/guestAuth/app/rest/")

  val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }

  val configurationName by lazy { systemProperties["teamcity.buildConfName"] }

  val buildParams by lazy { loadProperties(systemProperties["teamcity.configuration.properties.file"]) }

  fun getExistingParameter(name: String, impreciseNameMatch: Boolean = false): String {
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

  private val userName: String by lazy { getExistingParameter("teamcity.auth.userId") }
  private val password: String by lazy { getExistingParameter("teamcity.auth.password") }

  private fun <T : HttpRequest> T.withAuth(): T = this.apply {
    addHeader(BasicScheme().authenticate(UsernamePasswordCredentials(userName, password), this, null))
  }

  fun get(fullUrl: URI): JsonNode {
    val request = HttpGet(fullUrl).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      withAuth()
    }

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
      println("Request to TeamCity: $fullUrl")
    }

    val result = withRetry {
      HttpClient.sendRequest(request = request) {
        if (it.statusLine.statusCode != 200) {
          System.err.println(InputStreamReader(it.entity.content).readText())
          throw RuntimeException("TeamCity returned not successful status code ${it.statusLine.statusCode}")
        }

        jacksonObjectMapper().readTree(it.entity.content)
      }
    }

    return requireNotNull(result) { "Request ${request.uri} failed" }
  }

  fun downloadChangesPatch(buildTypeId: String, modificationId: String, useCachedResult: Boolean = true, isPersonal: Boolean): String {
    val uri = baseUri.resolve("/downloadPatch.html?buildTypeId=${buildTypeId}&modId=${modificationId}&personal=$isPersonal")

    if (useCachedResult) {
      val changesPatch = getDataFromCache(uri)
      if (changesPatch != null) {
        if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
          println("Returning cached result for $uri")
        }
        return changesPatch
      }
    }

    val outputStream = ByteArrayOutputStream()

    if (!HttpClient.download(request = HttpGet(uri).withAuth(), outStream = outputStream, retries = 3)) {
      throw RuntimeException("Couldn't download patch $uri in 3 attempts")
    }

    val changesPatch = outputStream.toString("UTF-8")
    cacheDir.createDirectories()

    putDataToCache(uri, changesPatch)
    return changesPatch
  }

  fun downloadChangesPatch(modificationId: String, useCachedResult: Boolean = true, isPersonal: Boolean): String =
    downloadChangesPatch(buildTypeId = buildTypeId,
                         modificationId = modificationId,
                         useCachedResult = useCachedResult,
                         isPersonal = isPersonal)

  fun getChanges(buildId: String): List<JsonNode> {
    val fullUrl = restUri.resolve("changes?locator=build:(id:$buildId)")
    val changes = get(fullUrl).fields().asSequence()
      .filter { it.key == "change" }
      .flatMap { it.value }
      .toList()

    return changes
  }

  fun getChanges() = getChanges(buildId)

  fun getTestRunInfo(buildId: String): List<JsonNode> {
    val countOfTestsOnPage = 200
    var startPosition = 0
    val accumulatedTests: MutableList<JsonNode> = mutableListOf()

    var currentTests: List<JsonNode>

    println("Getting test run info from TC ...")

    do {
      val fullUrl = restUri
        .resolve("testOccurrences?locator=build:(id:$buildId),count:$countOfTestsOnPage,start:$startPosition" +
                 "&includePersonal=true&fields=testOccurrence(id,name,status,duration,runOrder)")

      currentTests = get(fullUrl).fields().asSequence()
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

  fun getTestRunInfo() = getTestRunInfo(buildId)
}

