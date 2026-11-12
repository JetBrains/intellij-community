// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers

import com.intellij.ide.startup.importSettings.db.WindowsEnvVariables
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities.VSHive
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

class VSInstanceStateParser(private val stateFile: Path) {
  companion object {
    private val logger = logger<VSInstanceStateParser>()

    /**
     * @return the [VSInstanceStateParser] for the state file of [hive], or `null` when the hive has no instance ID or the file does not exist.
     */
    fun create(hive: VSHive): VSInstanceStateParser? {
      val instanceId = hive.instanceId ?: return null
      val programData = WindowsEnvVariables.programData ?: run {
        logger.info("ProgramData is not set")
        return null
      }

      val stateFile = Path(programData, "Microsoft", "VisualStudio", "Packages", "_Instances", instanceId, "state.json")
      if (!stateFile.isRegularFile()) {
        logger.info("No instance state file for ${hive.hiveString}: $stateFile")
        return null
      }
      return VSInstanceStateParser(stateFile)
    }
  }

  private val root: JsonNode? by lazy {
    try {
      stateFile.inputStream().use { JsonMapper.builder().build().readTree(it) }
    }
    catch (t: Throwable) {
      logger.warn("Failed to read $stateFile", t)
      null
    }
  }

  /**
   * The VS product title from state file, e.g. "Visual Studio Professional 2026".
   * Using `en-us` title, or the first title when `en-us` is absent.
   */
  val title: @NlsSafe String? by lazy {
    val resources = root?.get("localizedResources")?.takeIf { it.isArray } ?: return@lazy null
    val titles = resources.mapNotNull { resource ->
      val title = resource["title"]?.takeIf { it.isString }?.asString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      resource["language"]?.takeIf { it.isString }?.asString()?.lowercase() to title
    }
    titles.firstOrNull { it.first == "en-us" }?.second ?: titles.firstOrNull()?.second
  }
}
