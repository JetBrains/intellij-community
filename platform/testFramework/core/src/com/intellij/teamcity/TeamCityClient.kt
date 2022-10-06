package com.intellij.teamcity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.util.*


object TeamCityClient {
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
  }

  private val baseUrl = URI("https://buildserver.labs.intellij.net").normalize()

  private val restUrl = baseUrl.resolve("/app/rest/")
  private val guestAuthUrl = baseUrl.resolve("/guestAuth/app/rest/")

  val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }

  val configurationName by lazy { systemProperties["teamcity.buildConfName"] }

  val buildParams by lazy { loadProperties(systemProperties["teamcity.configuration.properties.file"]) }

  fun getExistingParameter(name: String): String {
    return buildParams[name] ?: error("Parameter $name is not specified in the build!")
  }

  val buildId: String by lazy { getExistingParameter("teamcity.build.id") }
  val buildTypeId: String by lazy { getExistingParameter("teamcity.buildType.id") }

  private val userName: String by lazy { System.getProperty("pin.builds.user.name") }
  private val password: String by lazy { System.getProperty("pin.builds.user.password") }

  private fun <T : HttpRequest> T.withAuth(): T = this.apply {
    addHeader(BasicScheme().authenticate(UsernamePasswordCredentials(userName, password), this, null))
  }

  fun get(fullUrl: URI): JsonNode {
    val request = HttpGet(fullUrl).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
      withAuth()
    }

    println("Request to TeamCity: $fullUrl")

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

  fun downloadChangesPatch(buildTypeId: String, modificationId: String): String {
    val url = baseUrl.resolve("/downloadPatch.html?buildTypeId=${buildTypeId}&modId=${modificationId}")
    val outputStream = ByteArrayOutputStream()

    if (!HttpClient.download(request = HttpGet(url).withAuth(), outStream = outputStream, retries = 3)) {
      throw RuntimeException("Couldn't download $url in 3 attempts")
    }

    return outputStream.toString("UTF-8")
  }

  fun downloadChangesPatch(modificationId: String) = downloadChangesPatch(buildTypeId, modificationId)

  fun getChanges(buildId: String): List<JsonNode> {
    val fullUrl = restUrl.resolve("changes?locator=build:(id:$buildId)")
    val changes = get(fullUrl).fields().asSequence()
      .filter { it.key == "change" }
      .flatMap { it.value }
      .toList()

    return changes
  }

  fun getChanges() = getChanges(buildId)
}

