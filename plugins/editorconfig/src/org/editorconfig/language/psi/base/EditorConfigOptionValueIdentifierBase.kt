// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier
import org.editorconfig.language.psi.impl.EditorConfigValueIdentifierDescriptorFinderVisitor
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnsetValueDescriptor
import org.editorconfig.language.services.EditorConfigElementFactory

abstract class EditorConfigOptionValueIdentifierBase(node: ASTNode) :
  EditorConfigIdentifierElementBase(node), EditorConfigOptionValueIdentifier {

  final override fun getDescriptor(smart: Boolean): EditorConfigDescriptor? {
    val parent = describableParent ?: return null
    val visitor = EditorConfigValueIdentifierDescriptorFinderVisitor(this)
    val parentDescriptor = parent.getDescriptor(smart) ?: return null
    parentDescriptor.accept(visitor)
    return visitor.descriptor ?: unsetDescriptor
  }

  private val unsetDescriptor
    get() = if (EditorConfigUnsetValueDescriptor.matches(this)) EditorConfigUnsetValueDescriptor else null

  final override fun setName(newName: String): PsiElement {
    val factory = EditorConfigElementFactory.getInstance(project)
    val result = factory.createValueIdentifier(newName)
    replace(result)
    return result
  }
}
