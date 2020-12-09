// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project

private class ProductVersionTitleInfoProvider : SimpleTitleInfoProvider(VMOOption("ide.ui.version.in.title")) {
  override fun getValue(project: Project) = ApplicationInfo.getInstance().fullVersion
}