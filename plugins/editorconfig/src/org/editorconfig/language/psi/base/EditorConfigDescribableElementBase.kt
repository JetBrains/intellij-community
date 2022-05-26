// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiReference
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.psi.reference.EditorConfigConstantReference
import org.editorconfig.language.psi.reference.EditorConfigDeclarationReference
import org.editorconfig.language.psi.reference.EditorConfigIdentifierReference
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.getParentOfType

abstract class EditorConfigDescribableElementBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigDescribableElement {
  final override val option: EditorConfigOption
    get() = getParentOfType() ?: throw IllegalStateException()

  final override val section: EditorConfigSection
    get() = getParentOfType() ?: throw IllegalStateException()

  override val describableParent: EditorConfigDescribableElement?
    get() = parent as? EditorConfigDescribableElement

  override val declarationSite: String
    get() {
      val header = section.header.text
      val virtualFile = containingFile.virtualFile ?: return header
      val fileName = virtualFile.presentableName
      return "$header ($fileName)"
    }


  override fun getReference(): PsiReference? {
    val descriptor = getDescriptor(false)
    return when (descriptor) {
      is EditorConfigDeclarationDescriptor -> EditorConfigDeclarationReference(this)
      is EditorConfigReferenceDescriptor -> EditorConfigIdentifierReference(this, descriptor.id)
      is EditorConfigConstantDescriptor -> EditorConfigConstantReference(this)
      is EditorConfigUnionDescriptor -> {
        if (EditorConfigDescriptorUtil.isConstant(descriptor)) {
          EditorConfigConstantReference(this)
        }
        else {
          logger<EditorConfigDescribableElementBase>().warn("Got non-constant union")
          null
        }
      }
      else -> null
    }
  }

  final override fun toString(): String = text
}
