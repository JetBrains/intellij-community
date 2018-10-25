// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
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

  override fun getPresentation() = PresentationData(text, this.declarationSite, AllIcons.Nodes.Property, null)
}
