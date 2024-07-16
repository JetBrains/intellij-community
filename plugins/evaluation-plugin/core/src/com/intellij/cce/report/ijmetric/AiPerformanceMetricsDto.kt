package com.intellij.cce.report.ijmetric

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.metrics.collector.metrics.MetricGroup
import com.intellij.tools.ide.metrics.collector.publishing.CIServerBuildInfo
import com.intellij.util.system.OS
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


/*
  copy @com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
  AI metrics requires Double value, but we don't want to change basic types much.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AiPerformanceMetricsDto(
  val version: String,
  val generated: String,
  val project: String,
  val projectURL: String,
  val projectDescription: String,
  val os: String,
  val osFamily: String,
  val runtime: String,
  val build: String,
  val buildDate: String,
  val branch: String,
  val productCode: String,
  val methodName: String,
  val metrics: List<AiApplicationMetricDto>,
  val systemMetrics: Map<String, List<MetricGroup>>,
  val tcInfo: CIServerBuildInfo
) {
  companion object {
    private const val VERSION = "1"

    @JvmStatic
    fun create(
      projectName: String,
      projectURL: String,
      projectDescription: String,
      methodName: String,
      buildNumber: BuildNumber,
      metrics: List<AiApplicationMetricDto>,
      buildInfo: CIServerBuildInfo,
      generated: String = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
    ) = AiPerformanceMetricsDto(
      version = VERSION,
      generated = generated,
      project = projectName,
      projectURL = projectURL,
      os = SystemInfo.getOsNameAndVersion(),
      osFamily = OS.CURRENT.toString(),
      runtime = SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION,
      build = buildNumber.asStringWithoutProductCode(),
      branch = buildNumber.asStringWithoutProductCode().substringBeforeLast("."),
      // the 'buildDate' field is required for https://ij-perf.jetbrains.com; use any value here
      buildDate = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME),
      productCode = buildNumber.productCode,
      metrics = metrics,
      methodName = methodName,
      systemMetrics = mapOf(),
      tcInfo = buildInfo,
      projectDescription = projectDescription
    )
  }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class AiApplicationMetricDto(
  val n: String,
  val c: Double,
)