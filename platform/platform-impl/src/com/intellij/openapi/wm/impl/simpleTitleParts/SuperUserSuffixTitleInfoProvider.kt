// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.util.animation.Animation.withContext
import com.intellij.util.io.SuperUserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SuperUserSuffixTitleInfoProvider : SimpleTitleInfoProvider(TitleInfoOption.ALWAYS_ACTIVE) {
  init {
    SuperUserStatus.whenKnown {
      withContext(Dispatchers.UI) {
        updateNotify()
      }
    }
  }

  override fun getValue(project: Project): String {
    val result = ProjectFrameHelper.superUserSuffix ?: return ""
    return if (IdeFrameDecorator.isCustomDecorationActive()) result else "($result)"
  }

  override val borderlessPrefix = " - "
}