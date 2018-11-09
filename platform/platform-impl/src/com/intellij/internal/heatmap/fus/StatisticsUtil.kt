// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.heatmap.fus

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.heatmap.actions.LOG
import com.intellij.internal.heatmap.actions.MetricEntity
import com.intellij.internal.heatmap.actions.ProductBuildInfo
import com.intellij.internal.heatmap.actions.ShowHeatMapAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.ContainerUtil
import com.intellij.xml.util.XmlStringUtil
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.util.*


val DEFAULT_SERVICE_URLS = arrayListOf("https://prod.fus.aws.intellij.net", "https://qa.fus.aws.intellij.net")
private const val METRICS_PATH = "/peacock/api/rest/v1/metrics"
private const val ASYNC = "/async"
private const val DATA_SERVICE_URL = "https://data.services.jetbrains.com/products"

fun fetchStatistics(dateFrom: String,
                    dateTo: String,
                    productCode: String,
                    buildNumbers: List<String>,
                    groups: List<String>,
                    accessToken: String,
                    filter: String? = null,
                    minUsers: Int = 5): List<MetricEntity> {
  myAccessToken = accessToken
  val query = formQuery(dateFrom, dateTo, productCode, buildNumbers, groups, StringUtil.notNullize(filter))

  val postAsync = async()
  val entity = StringEntity(query, ContentType.APPLICATION_JSON)
  postAsync.entity = entity
  val httpClient = HttpClientBuilder.create().build()
  val response: CloseableHttpResponse?
  return try {
    LOG.debug("Executing request: $postAsync")
    response = httpClient.execute(postAsync)
    LOG.debug("Response: $response")
    if (response.statusLine.statusCode != 200) {
      val message = "Request failed with code: ${response.statusLine.statusCode}. Reason: ${response.statusLine.reasonPhrase}"
      LOG.warn(message)
      var placeMessage = "group: $groups"
      if (filter != null && filter.isNotBlank()) placeMessage += ", $filter"
      showWarnNotification("Statistics fetch failed for $placeMessage", message)
      return emptyList()
    }
    getStats(response, minUsers)
  }
  catch (e: Exception) {
    LOG.warn("Request failed: ${e.message};\n${e.stackTrace}")
    emptyList()
  }
}

fun getStats(asyncResponse: HttpResponse, minUsers: Int): List<MetricEntity> {
  try {
    val asyncEntity = asyncResponse.entity ?: return emptyList()
    var responseEntity = EntityUtils.toString(asyncEntity) ?: return emptyList()

    val httpClient = HttpClientBuilder.create().build()
    fun execute(httpRequest: HttpUriRequest): String? {
      LOG.info("Executing request: $httpRequest")
      val httpEntity = httpClient.execute(httpRequest)?.entity ?: return null
      val resultString = EntityUtils.toString(httpEntity)
      LOG.debug("Got result: $resultString")
      return resultString
    }

    val queryId = getQueryId(responseEntity) ?: return emptyList()
    var status = getQueryStatus(responseEntity)
    while ("running".equals(status, true)) {
      Thread.sleep(500)
      responseEntity = execute(status(queryId)) ?: break
      status = getQueryStatus(responseEntity)
    }
    val rowsResult = mutableListOf<MetricEntity>()
    LOG.info("Got JSON result: $responseEntity")
    var rows = parseRows(responseEntity)
    rowsResult.addAll(parseRows(responseEntity))

    var nextPageToken = getNextPageToken(responseEntity)
    while (nextPageToken != null && rows.isNotEmpty() && rows[0].users >= minUsers) {
      Thread.sleep(500)
      responseEntity = execute(queries(queryId, nextPageToken)) ?: break
      rows = parseRows(responseEntity)
      rowsResult.addAll(rows)
      nextPageToken = getNextPageToken(responseEntity)
    }
    return rowsResult
  }
  catch (e: Exception) {
    LOG.warn("Error: ${e.message};\n${e.stackTrace}")
    return emptyList()
  }
}


fun queries(queryId: String, pageToken: String): HttpGet {
  val params = ArrayList<NameValuePair>()
  params.add(BasicNameValuePair("nextPageToken", pageToken))
  params.add(BasicNameValuePair("queryId", queryId))
  return metricsGet("/queries", params)
}

fun parseRows(string: String): List<MetricEntity> {
  val json = JsonParser().parse(string)
  val rows = json.asJsonObject.get("rows") ?: return emptyList()
  val result = mutableListOf<MetricEntity>()
  for (el in rows.asJsonArray) {
    val stat = createMetricEntity(el)
    if (stat != null) result.add(stat)
  }
  return result
}

private fun createMetricEntity(el: JsonElement): MetricEntity? {
  val o = el.asJsonObject
  val metricId = o.get("metric_id")?.asString ?: return null
  val sampleSize = o.get("sample_size")?.asInt ?: return null
  val groupSize = o.get("group_size")?.asInt ?: return null
  val metricUsers = o.get("metric_users")?.asInt ?: return null
  val metricUsages = o.get("metric_usages")?.asInt ?: return null
  val usagesPerUser = (metricUsages / metricUsers.toFloat())
  val metricShare = metricUsers * 100 / sampleSize.toFloat() //metric_users * 100 / sample_size 
  return MetricEntity(metricId, sampleSize, groupSize, metricUsers, metricUsages, usagesPerUser, metricShare)
}

fun getNextPageToken(string: String): String? {
  val json = JsonParser().parse(string)
  val queryId = json.asJsonObject.get("nextPageToken")
  return queryId?.asString
}

private fun status(queryId: String): HttpGet {
  return metricsGet("/$queryId/status")
}

private fun async(): HttpPost {
  return metrics(ASYNC)
}

private fun metrics(path: String): HttpPost {
  val url = ShowHeatMapAction.getSelectedServiceUrl() + METRICS_PATH + path
  return postRequest(url)
}

private fun metricsGet(path: String, params: MutableList<NameValuePair>? = null): HttpGet {
  val url = ShowHeatMapAction.getSelectedServiceUrl() + METRICS_PATH + path
  return getRequest(url, params)
}

private fun postRequest(url: String): HttpPost {
  val uriBuilder = URIBuilder(url)
  val uri = uriBuilder.build()
  val request = HttpPost(uri)

  request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)

  val token = getAccessToken()
  val authHeader = "Bearer $token"
  request.setHeader(HttpHeaders.AUTHORIZATION, authHeader)
  return request
}

private var myAccessToken: String? = null

private fun getAccessToken(): String? {
  return myAccessToken
}

private fun getRequest(url: String, params: List<NameValuePair>?): HttpGet {
  val uriBuilder = URIBuilder(url)
  if (params != null) uriBuilder.addParameters(params)
  val uri = uriBuilder.build()
  val request = HttpGet(uri)

  request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)

  val token = getAccessToken()
  val authHeader = "Bearer $token"
  request.setHeader(HttpHeaders.AUTHORIZATION, authHeader)
  return request
}

private fun getProductJson(productCode: String): JsonObject? {
  val jsonResult = execute(jsonGetRequest(DATA_SERVICE_URL))
  val json = JsonParser().parse(jsonResult)
  return findProduct(json.asJsonArray, productCode)
}

fun getProductBuildInfos(productCode: String): List<ProductBuildInfo> {
  val productJson = getProductJson(productCode)
  if (productJson == null) return emptyList()
  return parseProductReleases(productJson, productCode)
}

fun parseProductReleases(productJson: JsonObject, productCode: String): List<ProductBuildInfo> {
  val releases = productJson.get("releases").asJsonArray
  val result = mutableListOf<ProductBuildInfo>()
  for (release in releases) {
    val type = release.asJsonObject.get("type")?.asString
    val version = release.asJsonObject.get("version")?.asString
    val majorVersion = release.asJsonObject.get("majorVersion")?.asString
    val build = release.asJsonObject.get("build")?.asString
    if (type != null && version != null && majorVersion != null && build != null) result.add(
      ProductBuildInfo(productCode, type, version, majorVersion, build))
  }
  return result
}

fun findProduct(products: JsonArray, productCode: String): JsonObject? {
  products.forEach { product ->
    val code = product.asJsonObject.get("code")?.asString
    if (productCode == code) return product.asJsonObject

    val altCodes = product.asJsonObject.get("alternativeCodes")?.asJsonArray
    if (altCodes?.find { it.asString == productCode } != null) return product.asJsonObject
  }
  return null
}

fun jsonGetRequest(url: String, params: List<NameValuePair>? = null): HttpGet {
  val uriBuilder = URIBuilder(url)
  if (params != null) uriBuilder.addParameters(params)
  val uri = uriBuilder.build()
  val request = HttpGet(uri)
  request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
  return request
}

fun execute(httpRequest: HttpUriRequest): String? {
  val httpClient = HttpClientBuilder.create().build()
  LOG.debug("Executing request: $httpRequest")
  val httpEntity = httpClient.execute(httpRequest)?.entity ?: return null
  val resultString = EntityUtils.toString(httpEntity)
  LOG.debug("Got result: $resultString")
  return resultString
}

fun getQueryStatus(response: String): String? {
  val json = JsonParser().parse(response)
  val queryId = json.asJsonObject.get("status")
  return queryId?.asString
}

fun getQueryId(response: String): String? {
  val json = JsonParser().parse(response)
  val queryId = json.asJsonObject.get("queryId")
  return queryId?.asString
}

fun formQuery(dateFrom: String, dateTo: String, productCode: String, builds: List<String>, groups: List<String>, filter: String): String {
  val sb = StringBuilder()
  sb.append("{\"dateFrom\":\"$dateFrom\",\"dateTo\":\"$dateTo\",\"products\":[\"$productCode\"],\"builds\":[")
  builds.forEachIndexed { i, v ->
    sb.append("\"$v\"")
    if (i < builds.size - 1) sb.append(",")
  }
  sb.append("],\"groups\":[")
  groups.forEachIndexed { i, s ->
    sb.append("\"$s\"")
    if (i < groups.size - 1) sb.append(",")
  }
  sb.append("],\"filter\":\"$filter\"}")
  LOG.debug("Formed query: $sb")
  return sb.toString()
}

fun getBuildsForIDEVersions(ideVersions: List<String>, includeEap: Boolean): List<String> {
  val result = ContainerUtil.newArrayList<String>()
  for (buildInfo in ShowHeatMapAction.getOurIdeBuildInfos()) {
    if (!includeEap && buildInfo.isEap()) continue
    if (ideVersions.contains(buildInfo.version)) result.add(buildInfo.build)
  }
  return result
}

fun filterByPlaceToolbar(toFilter: List<MetricEntity>, place: String): List<MetricEntity> {
  val filtered = mutableListOf<MetricEntity>()
  toFilter.forEach {
    if (place.equals(getToolBarButtonPlace(it), true)) {
      filtered.add(it)
    }
  }
  return filtered
}

fun filterByMenuName(toFilter: List<MetricEntity>, menuName: String): List<MetricEntity> {
  val filtered = mutableListOf<MetricEntity>()
  toFilter.forEach {
    if (menuName.equals(getMenuName(it), true)) {
      filtered.add(it)
    }
  }
  return filtered
}

fun showWarnNotification(title: String, message: String, project: Project? = null) {
  val warn = Notification("IDE Click Map", PlatformIcons.WARNING_INTRODUCTION_ICON, title, null, XmlStringUtil.wrapInHtml(message),
                          NotificationType.WARNING, null)
  Notifications.Bus.notify(warn, project)
}


fun getToolBarButtonPlace(metricEntity: MetricEntity): String = metricEntity.id.substringAfter('@')

fun getMenuName(metricEntity: MetricEntity): String = metricEntity.id.substringBefore("->").trim()