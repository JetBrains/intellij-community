// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers

import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

internal class TestPartialImportPerformer(
  private val willPerform: Boolean,
  private val requiredPlugins: Set<PluginId> = emptySet(),
) : PartialImportPerformer {
  val performCalls: MutableList<Settings> = mutableListOf()
  val performEdtCalls: MutableList<Settings> = mutableListOf()

  override fun willPerform(settings: Settings): Boolean = willPerform

  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> = requiredPlugins

  override fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings = settings

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {
    performCalls.add(settings)
  }

  override fun performEdt(project: Project?, settings: Settings) {
    performEdtCalls.add(settings)
  }
}
