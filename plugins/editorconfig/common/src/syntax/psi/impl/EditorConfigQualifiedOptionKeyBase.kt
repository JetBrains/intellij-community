// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedOptionKey
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons

abstract class EditorConfigQualifiedOptionKeyBase(node: ASTNode) : EditorConfigDescribableElementBase(
  node), EditorConfigQualifiedOptionKey {

  override fun getName(): String = text

  override fun getPresentation(): PresentationData = PresentationData(text, this.declarationSite, IconManager.getInstance().getPlatformIcon(
    PlatformIcons.Property), null)
}
