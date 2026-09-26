// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates.settings

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
  name = "ComposeResourcesEmmetLikeSettings",
  storages = [Storage("composeResourcesEmmetLikeSettings.xml")],
  category = SettingsCategory.CODE,
)
internal class ComposeResourcesEmmetLikeSettings : PersistentStateComponent<ComposeResourcesEmmetLikeSettings> {
  var expandShortcut: Int = TemplateSettings.TAB_CHAR.code

  override fun getState(): ComposeResourcesEmmetLikeSettings = this

  override fun loadState(state: ComposeResourcesEmmetLikeSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): ComposeResourcesEmmetLikeSettings =
      ApplicationManager.getApplication().service<ComposeResourcesEmmetLikeSettings>()
  }
}