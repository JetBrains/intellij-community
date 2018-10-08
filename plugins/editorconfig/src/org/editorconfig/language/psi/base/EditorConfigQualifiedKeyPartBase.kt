// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigQualifiedKeyPart
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.services.EditorConfigElementFactory

abstract class EditorConfigQualifiedKeyPartBase(node: ASTNode) : EditorConfigIdentifierElementBase(node), EditorConfigQualifiedKeyPart {
  final override fun getDescriptor(smart: Boolean): EditorConfigDescriptor? {
    val qualifiedKey = parent as EditorConfigQualifiedOptionKey
    val qualifiedKeyParts = qualifiedKey.qualifiedKeyPartList
    val parts = qualifiedKey.getDescriptor(smart)?.children ?: return null
    val index = qualifiedKeyParts.indexOf(this)
    val result = parts[index]
    return if (result.matches(this)) result else null
  }

  final override fun setName(newName: String): PsiElement {
    val factory = EditorConfigElementFactory.getInstance(project)
    val result = factory.createKeyPart(newName)
    replace(result)
    return result
  }
}
