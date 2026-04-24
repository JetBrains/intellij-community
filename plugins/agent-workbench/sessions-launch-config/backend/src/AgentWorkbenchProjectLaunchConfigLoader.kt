// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.diagnostic.debug
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private const val AGENT_WORKBENCH_PROJECT_CONFIG_FILE_NAME: String = ".agent-workbench.yaml"

private val YAML_MAPPER = ObjectMapper(YAMLFactory())

internal class AgentWorkbenchProjectLaunchConfigCache {
  companion object {
    val shared: AgentWorkbenchProjectLaunchConfigCache = AgentWorkbenchProjectLaunchConfigCache()
  }

  private val configByProjectRoot = ConcurrentHashMap<Path, AgentWorkbenchProjectLaunchConfig>()

  fun getProviderConfig(projectRoot: Path, provider: AgentSessionProvider): AgentWorkbenchLaunchConfig? {
    val normalizedProjectRoot = projectRoot.normalize()
    return configByProjectRoot.computeIfAbsent(normalizedProjectRoot, ::loadAgentWorkbenchProjectLaunchConfig)
      .resolveFor(provider)
  }
}

private fun loadAgentWorkbenchProjectLaunchConfig(projectRoot: Path): AgentWorkbenchProjectLaunchConfig {
  val configPath = projectRoot.resolve(AGENT_WORKBENCH_PROJECT_CONFIG_FILE_NAME)
  if (!Files.isRegularFile(configPath)) {
    AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.debug {
      "No Agent Workbench config found for projectRoot=$projectRoot at $configPath"
    }
    return AgentWorkbenchProjectLaunchConfig.EMPTY
  }

  val rootNode = try {
    Files.newBufferedReader(configPath, StandardCharsets.UTF_8).use(YAML_MAPPER::readTree)
  }
  catch (t: Throwable) {
    AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Failed to parse Agent Workbench config $configPath", t)
    return AgentWorkbenchProjectLaunchConfig.EMPTY
  } ?: return AgentWorkbenchProjectLaunchConfig.EMPTY

  val sharedConfig = readLaunchConfig(
    configNode = rootNode,
    projectRoot = projectRoot,
    configPath = configPath,
    fieldPrefix = null,
  ) ?: AgentWorkbenchLaunchConfig.EMPTY

  val providerConfigs = LinkedHashMap<String, AgentWorkbenchLaunchConfig>()
  val providersNode = rootNode.get("providers")
  if (providersNode != null && !providersNode.isNull) {
    if (!providersNode.isObject) {
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench config $configPath: 'providers' must be an object")
    }
    else {
      for ((providerName, providerNode) in providersNode.properties()) {
        if (!providerNode.isObject) {
          AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn(
            "Ignoring Agent Workbench config $configPath: 'providers.$providerName' must be an object"
          )
          continue
        }

        val providerConfig = readLaunchConfig(
          configNode = providerNode,
          projectRoot = projectRoot,
          configPath = configPath,
          fieldPrefix = "providers.$providerName",
        ) ?: continue
        providerConfigs.putIfAbsent(providerName, providerConfig)
      }
    }
  }

  val config = if (sharedConfig.isEmpty() && providerConfigs.isEmpty()) {
    AgentWorkbenchProjectLaunchConfig.EMPTY
  }
  else {
    AgentWorkbenchProjectLaunchConfig(sharedConfig = sharedConfig, providers = providerConfigs)
  }
  AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.debug {
    if (config.sharedConfig.isEmpty() && config.providers.isEmpty()) {
      "Loaded Agent Workbench config $configPath but it does not define any valid launch augmentation entries"
    }
    else {
      "Loaded Agent Workbench config $configPath: ${config.toDebugSummary()}"
    }
  }
  return config
}

private fun readLaunchConfig(
  configNode: JsonNode,
  projectRoot: Path,
  configPath: Path,
  fieldPrefix: String?,
): AgentWorkbenchLaunchConfig? {
  val pathPrepend = readPathList(
    node = configNode.get("pathPrepend"),
    projectRoot = projectRoot,
    configPath = configPath,
    fieldName = configFieldName(fieldPrefix, "pathPrepend"),
  )
  val commandShims = readPathMap(
    node = configNode.get("commandShims"),
    projectRoot = projectRoot,
    configPath = configPath,
    fieldName = configFieldName(fieldPrefix, "commandShims"),
  )

  if (pathPrepend.isEmpty() && commandShims.isEmpty()) {
    return null
  }

  return AgentWorkbenchLaunchConfig(
    pathPrepend = pathPrepend,
    commandShims = commandShims,
  )
}

private fun configFieldName(fieldPrefix: String?, fieldName: String): String {
  return if (fieldPrefix == null) fieldName else "$fieldPrefix.$fieldName"
}

private fun readPathList(
  node: JsonNode?,
  projectRoot: Path,
  configPath: Path,
  fieldName: String,
): List<Path> {
  if (node == null || node.isNull) {
    return emptyList()
  }
  if (!node.isArray) {
    AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench config $configPath: '$fieldName' must be a list of strings")
    return emptyList()
  }

  val result = ArrayList<Path>()
  for (entry in node) {
    if (!entry.isTextual) {
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn(
        "Ignoring Agent Workbench config $configPath: '$fieldName' contains a non-string entry"
      )
      continue
    }
    resolveConfiguredPath(projectRoot, entry.asText(), configPath, fieldName)?.let(result::add)
  }
  return result
}

private fun readPathMap(
  node: JsonNode?,
  projectRoot: Path,
  configPath: Path,
  fieldName: String,
): Map<String, Path> {
  if (node == null || node.isNull) {
    return emptyMap()
  }
  if (!node.isObject) {
    logInvalidStringMap(configPath, fieldName)
    return emptyMap()
  }

  val result = LinkedHashMap<String, Path>()
  for ((name, value) in node.properties()) {
    if (!value.isTextual) {
      logInvalidStringValue(configPath, "$fieldName.$name")
      continue
    }
    val resolvedPath = resolveConfiguredPath(projectRoot, value.asText(), configPath, "$fieldName.$name") ?: continue
    result.putIfAbsent(name, resolvedPath)
  }
  return result
}

private fun resolveConfiguredPath(
  projectRoot: Path,
  rawPath: String,
  configPath: Path,
  fieldName: String,
): Path? {
  val parsedPath = parseAgentWorkbenchPathOrNull(rawPath)
  if (parsedPath == null) {
    AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench config $configPath: '$fieldName' is not a valid path")
    return null
  }
  return if (parsedPath.isAbsolute) parsedPath.normalize() else projectRoot.resolve(parsedPath).normalize()
}

private fun logInvalidStringMap(configPath: Path, fieldName: String) {
  AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench config $configPath: '$fieldName' must be an object of strings")
}

private fun logInvalidStringValue(configPath: Path, fieldName: String) {
  AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench config $configPath: '$fieldName' must be a string")
}

internal data class AgentWorkbenchProjectLaunchConfig(
  val sharedConfig: AgentWorkbenchLaunchConfig,
  val providers: Map<String, AgentWorkbenchLaunchConfig>,
) {
  fun resolveFor(provider: AgentSessionProvider): AgentWorkbenchLaunchConfig? {
    val resolvedConfig = sharedConfig.merge(providers[provider.value])
    return resolvedConfig.takeUnless(AgentWorkbenchLaunchConfig::isEmpty)
  }

  companion object {
    val EMPTY: AgentWorkbenchProjectLaunchConfig = AgentWorkbenchProjectLaunchConfig(AgentWorkbenchLaunchConfig.EMPTY, emptyMap())
  }
}

private fun AgentWorkbenchProjectLaunchConfig.toDebugSummary(): String {
  return "shared={${sharedConfig.toDebugSummary()}}, providers=${providers.keys.sorted()}"
}

internal data class AgentWorkbenchLaunchConfig(
  val pathPrepend: List<Path>,
  val commandShims: Map<String, Path>,
) {
  fun isEmpty(): Boolean = pathPrepend.isEmpty() && commandShims.isEmpty()

  fun merge(override: AgentWorkbenchLaunchConfig?): AgentWorkbenchLaunchConfig {
    if (override == null || override.isEmpty()) {
      return this
    }
    if (isEmpty()) {
      return override
    }

    val mergedPathPrepend = ArrayList<Path>(pathPrepend.size + override.pathPrepend.size)
    mergedPathPrepend.addAll(pathPrepend)
    mergedPathPrepend.addAll(override.pathPrepend)

    val mergedCommandShims = LinkedHashMap(commandShims)
    mergedCommandShims.putAll(override.commandShims)

    return AgentWorkbenchLaunchConfig(
      pathPrepend = mergedPathPrepend,
      commandShims = mergedCommandShims,
    )
  }

  companion object {
    val EMPTY: AgentWorkbenchLaunchConfig = AgentWorkbenchLaunchConfig(emptyList(), emptyMap())
  }
}

internal fun AgentWorkbenchLaunchConfig.toDebugSummary(): String {
  return "pathPrependCount=${pathPrepend.size}, commandShims=${commandShims.keys.sorted()}"
}
