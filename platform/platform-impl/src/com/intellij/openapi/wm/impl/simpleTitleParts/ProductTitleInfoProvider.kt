// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.IdeFrameDecorator

class ProductTitleInfoProvider(project: Project) : SimpleTitleInfoProvider(VMOOption("ide.ui.version.in.title")) {
  override fun isEnabled(): Boolean {
    return super.isEnabled() && if(IdeFrameDecorator.isCustomDecorationActive()) true else !SystemInfo.isMac && !SystemInfo.isGNOME
  }

  override val value: String = ApplicationNamesInfo.getInstance().fullProductName
  override val borderlessPrefix: String = " - "
}