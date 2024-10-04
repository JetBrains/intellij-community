// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.otherIde

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.collectors.fus.otherIde.LaunchJsonUsagesCollector.Companion.jsConfigurationEvent
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.util.io.isLocalHost
import java.net.URL

internal fun reportJSConfigurations(rootNode: JsonNode): List<MetricEvent> {
  val configurationsNode = rootNode.get("configurations")
  return if (configurationsNode.isArray) {
    configurationsNode.mapNotNull(::parseAndReportJSConfiguration)
  }
  else {
    emptyList()
  }
}

private fun parseAndReportJSConfiguration(node: JsonNode): MetricEvent? {
  if (!node.isObject) {
    return null
  }

  val configurationType = node.get("type")?.asText().let {
    if (it == null || it !in JavaScriptConfigurationFields.jsConfigurationTypes) {
      JavaScriptConfigurationFields.UNKNOWN_VALUE
    }
    else {
      it
    }
  }

  val request = node.get("request")?.asText().let {
    if (it == null || it !in JavaScriptConfigurationFields.requestTypes) {
      JavaScriptConfigurationFields.UNKNOWN_VALUE
    }
    else {
      it
    }
  }

  val hasCustomEnvVars = if (node.has("env")) {
    node.get("env")?.any() == true
  }
  else if (node.has("envFile")) {
    node.get("envFile")?.asText() != "\${workspaceFolder}/.env"
  }
  else false

  val skipFilesNode = node.get("skipFiles")
  val hasCustomSkipFiles = if (skipFilesNode != null && skipFilesNode.isArray) {
    skipFilesNode.any { it.asText() != "<node_internals>/**" }
  }
  else false

  val url = node.get("url")?.asText()

  return jsConfigurationEvent.metric(
    JavaScriptConfigurationFields.configurationType with configurationType,
    JavaScriptConfigurationFields.request with request,
    JavaScriptConfigurationFields.hasPreLaunchTask with node.has("preLaunchTask"),
    JavaScriptConfigurationFields.hasNonEmptyRuntimeArgs with (node.get("runtimeArgs")?.any() == true),
    JavaScriptConfigurationFields.hasCustomEnvVars with hasCustomEnvVars,
    JavaScriptConfigurationFields.hasCustomUrl with (url != null && url != "http://localhost:8080"),
    JavaScriptConfigurationFields.urlIsLocalHost with (url != null && isLocalHostUrl(url)),
    JavaScriptConfigurationFields.hasCustomPort with (node.get("port")?.asInt().let { it != null && it != 9229 && it != 0 }),
    JavaScriptConfigurationFields.hasCustomSkipFiles with hasCustomSkipFiles,
    JavaScriptConfigurationFields.hasPathMapping with (node.has("pathMapping") && node.get("pathMapping")?.isEmpty == false),
    JavaScriptConfigurationFields.hasCustomWebRoot with (node.has("webRoot") && node.get("webRoot")?.asText().let { it != null && it != "\${workspaceFolder}" && it != "\${workspaceFolder}/src" }),
    JavaScriptConfigurationFields.pauseForSourceMapEnabled with (node.get("pauseForSourceMap")?.booleanValue() == true),
  )
}

private fun isLocalHostUrl(url: String): Boolean {
  return try {
    val parsedUrl = URL(url)
    isLocalHost(parsedUrl.host)
  }
  catch (_: Exception) {
    false
  }
}

internal object JavaScriptConfigurationFields {
  const val UNKNOWN_VALUE = "unknown"

  val jsConfigurationTypes = setOf(
    "bun",
    "deno",
    "node",
    "chrome",
    "firefox",
    "msedge",
    "extensionHost",
    "node-terminal",
    "pwa-chrome",
    "pwa-extensionHost",
    "pwa-msedge",
    "pwa-node",
    UNKNOWN_VALUE,
  )

  val requestTypes = listOf("launch", "attach", UNKNOWN_VALUE)

  val configurationType = EventFields.String("configurationType", jsConfigurationTypes.toList())
  val request = EventFields.String("request", requestTypes)

  // similar to the 'Before launch' section
  val hasPreLaunchTask = EventFields.Boolean("hasPreLaunchTask")

  // arguments passed to interpreter
  val hasNonEmptyRuntimeArgs = EventFields.Boolean("hasNonEmptyRuntimeArgs")
  val hasCustomEnvVars = EventFields.Boolean("hasCustomEnvVars")

  // various fields important for debugger (we want to rework breakpoint placing strategy)
  // "url" field, default value is "http://localhost:8080"
  val hasCustomUrl = EventFields.Boolean("hasCustomUrl")
  val urlIsLocalHost = EventFields.Boolean("urlIsLocalHost")

  // "port" field, default value is 9229 or 0
  val hasCustomPort = EventFields.Boolean("hasCustomPort")

  // "skipFiles" field exists in configuration and contains non-default values (default is [ "<node_internals>/**" ])
  val hasCustomSkipFiles = EventFields.Boolean("hasCustomSkipFiles")

  // "pathMapping" field exists in configuration, like Remote URLs for local files in WebStorm
  val hasPathMapping = EventFields.Boolean("hasPathMapping")

  // "webRoot", default value is "${workspaceFolder}"
  val hasCustomWebRoot = EventFields.Boolean("hasCustomWebRoot")
  val pauseForSourceMapEnabled = EventFields.Boolean("pauseForSourceMapEnabled")
}