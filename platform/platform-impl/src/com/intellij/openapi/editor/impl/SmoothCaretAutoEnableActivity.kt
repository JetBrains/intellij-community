// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private const val AUTO_ENABLE_REASON_KEY = "editor.smooth.caret.auto.enable.reason"

private val LOG = logger<SmoothCaretAutoEnableActivity>()

@ApiStatus.Internal
enum class SmoothCaretAutoEnableReason {
  NONE,
  NEW_USER,
}

@ApiStatus.Internal
fun getSmoothCaretAutoEnableReason(): SmoothCaretAutoEnableReason {
  val value = PropertiesComponent.getInstance().getValue(AUTO_ENABLE_REASON_KEY) ?: return SmoothCaretAutoEnableReason.NONE
  return SmoothCaretAutoEnableReason.entries.find { it.name == value } ?: SmoothCaretAutoEnableReason.NONE
}

internal class SmoothCaretAutoEnableActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!InitialConfigImportState.isNewUser()) {
      return
    }

    withContext(Dispatchers.EDT) {
      val properties = PropertiesComponent.getInstance()
      if (properties.isValueSet(AUTO_ENABLE_REASON_KEY)) {
        return@withContext
      }

      LOG.info("Enabling smooth caret movement and blinking for a new user")

      val settings = EditorSettingsExternalizable.getInstance()
      settings.isSmoothCaretMovement = true
      settings.isSmoothBlinkCaret = true
      properties.setValue(AUTO_ENABLE_REASON_KEY, SmoothCaretAutoEnableReason.NEW_USER.name)
    }
  }
}
