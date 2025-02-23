// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor

abstract class EditorConfigQualifiedOptionKeyBase(node: ASTNode) : EditorConfigDescribableElementBase(
  node), EditorConfigQualifiedOptionKey {
  override fun getDescriptor(smart: Boolean): EditorConfigQualifiedKeyDescriptor? {
    val parent = parent as? EditorConfigOption ?: return null
    val key = parent.getDescriptor(smart)?.key ?: return null
    return key as? EditorConfigQualifiedKeyDescriptor
  }

  override fun getName(): String = text

  override fun getPresentation() = PresentationData(text, this.declarationSite, IconManager.getInstance().getPlatformIcon(
    PlatformIcons.Property), null)
}
