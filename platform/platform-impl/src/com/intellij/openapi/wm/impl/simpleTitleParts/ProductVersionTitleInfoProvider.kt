// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.simpleTitleParts

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.DefaultPartTitle

class ProductVersionTitleInfoProvider(project: Project) : SimpleTitleInfoProvider(VMOSubscription( "ide.ui.version.in.title"), RegistrySubscription("ide.borderless.title.version", project)) {
  override val value: String =  ApplicationInfo.getInstance().fullVersion
}