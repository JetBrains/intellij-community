// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*


private class JbAfterRestartSettingsApplier : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || !configPathFile.exists()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    if (!configPathFile.exists()) {
      return
    }
    asyncScope.async {
      try {
        val configLines = configPathFile.readLines()
        if (!FileUtil.delete(configPathFile.toFile())) {
          JbImportServiceImpl.LOG.warn("Couldn't delete $configPathFile, won't process config import")
          return@async
        }
        val oldConfDir = Path.of(configLines[0])
        val options = mutableSetOf<SettingsCategory>()
        configLines[1].split(",").forEach {
          options.add(SettingsCategory.valueOf(it.trim()))
        }
        val importer = JbSettingsImporter(oldConfDir, oldConfDir, null)
        withContext(Dispatchers.EDT) {
          importer.importOptions(options)
        }
      } catch (e: Throwable) {
        JbImportServiceImpl.LOG.warn("An exception occurred while importing $configPathFile", e)
      }
    }
  }

}

internal val configPathFile = PathManager.getConfigDir() / "after_restart_config.txt"
internal fun storeImportConfig(configDir: Path, categories: Set<SettingsCategory>) {
  configPathFile.writeText("$configDir\n${categories.joinToString()}")
}