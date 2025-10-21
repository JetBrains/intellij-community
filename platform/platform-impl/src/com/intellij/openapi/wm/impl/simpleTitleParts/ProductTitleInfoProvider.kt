// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.util.system.OS
import com.intellij.util.ui.UnixDesktopEnv

private class ProductTitleInfoProvider : SimpleTitleInfoProvider(VMOOption("ide.ui.version.in.title")) {
  override fun isEnabled(): Boolean =
    super.isEnabled() &&
    (IdeFrameDecorator.isCustomDecorationActive() || OS.CURRENT != OS.macOS && UnixDesktopEnv.CURRENT != UnixDesktopEnv.GNOME)

  override fun getValue(project: Project): String = ApplicationNamesInfo.getInstance().fullProductName
  override val borderlessPrefix = " - "
}
