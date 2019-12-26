// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.DefaultPartTitle

class ProductVersionTitleInfoProvider(project: Project) : SimpleTitleInfoProvider(project) {
  override val defaultRegistryKey: String? = "ide.ui.version.in.title"
  override val borderlessRegistryKey: String? = "ide.borderless.title.version"
  override val value: String =  ApplicationInfo.getInstance().fullVersion ?: ""
  override val borderlessTitlePart: DefaultPartTitle = DefaultPartTitle(" ")
}