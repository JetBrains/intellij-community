// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.CliResult
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SaveStarter private constructor() : ApplicationStarterBase(0) {
  override val usageMessage: String
    get() = IdeBundle.message("wrong.number.of.arguments.usage.ide.executable.save")

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    saveSettings(ApplicationManager.getApplication())
    return CliResult.OK
  }
}