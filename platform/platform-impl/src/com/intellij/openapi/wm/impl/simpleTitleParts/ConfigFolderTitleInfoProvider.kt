// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil

class ConfigFolderTitleInfoProvider : SimpleTitleInfoProvider(VMOOption("ide.config.folder.in.title")) {
  override fun getValue(project: Project): String {
    val path = FileUtil.getLocationRelativeToUserHome(PathManager.getConfigPath())
    return "($path)"
  }

  override fun isEnabled(): Boolean {
    // overridden because the base class enables the provider only if there are update listeners
    // todo fix the base class or provide a better way to overcome the base class behavior
    return option.isActive
  }
}