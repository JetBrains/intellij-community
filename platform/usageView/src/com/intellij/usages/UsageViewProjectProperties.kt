// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UsageViewProjectProperties(private val properties: PropertiesComponent) {
  companion object {
    @JvmStatic fun getInstance(project: Project): UsageViewProjectProperties = UsageViewProjectProperties(
      PropertiesComponent.getInstance(project)
    )
  }

  var isPreviewSource: Boolean
    get() = properties.isTrueValue(PREVIEW_SOURCE_KEY)
    set(value) {
      properties.setValue(PREVIEW_SOURCE_KEY, value)
    }
}

private const val PREVIEW_SOURCE_KEY = "ShowUsagesActions.previewPropertyKey"
