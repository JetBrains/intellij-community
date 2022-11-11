// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.ProjectFrameHelper

private class SuperUserSuffixTitleInfoProvider : SimpleTitleInfoProvider(TitleInfoOption.ALWAYS_ACTIVE) {
  override fun getValue(project: Project): String {
    val result = ProjectFrameHelper.superUserSuffix ?: return ""
    return if (IdeFrameDecorator.isCustomDecorationActive()) result else "($result)"
  }

  override val borderlessPrefix = " - "
}