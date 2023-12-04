// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.controllers

import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.models.FailedIdeVersion
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.ImportPerformer
import com.intellij.ide.startup.importSettings.providers.TransferSettingsPerformContext
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.*

interface TransferSettingsController {
  fun updateCheckboxes(ideVersion: IdeVersion)
  fun itemSelected(ideVersion: BaseIdeVersion)

  fun performImport(project: Project?, ideVersion: IdeVersion, pi: ProgressIndicator)
  fun performReload(ideVersion: FailedIdeVersion, pi: ProgressIndicator)

  fun addListener(listener: TransferSettingsListener)

  fun getImportPerformer(): ImportPerformer
}

interface TransferSettingsListener : EventListener {
  fun checkboxesUpdated(ideVersion: IdeVersion) {}

  fun reloadPerformed(ideVersion: FailedIdeVersion) {}

  fun importStarted(ideVersion: IdeVersion, settings: Settings) {}
  fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {}
  fun importPerformed(ideVersion: IdeVersion, settings: Settings, context: TransferSettingsPerformContext) {}

  fun itemSelected(ideVersion: BaseIdeVersion) {}
}