// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.documentation

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor

class EditorConfigElementDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
    if (element !is EditorConfigDescribableElement) return null
    if (element is EditorConfigFlatOptionKey) {
      return EditorConfigBundle.get("usage.type.option.key", element.text, element.section.header.text)
    }

    return when (element.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor,
      is EditorConfigReferenceDescriptor -> EditorConfigBundle.get(
        "usage.type.identifier",
        element.text,
        element.section.header.text
      )
      is EditorConfigConstantDescriptor -> EditorConfigBundle.get(
        "usage.type.constant",
        element.text,
        element.section.header.text
      )
      else -> EditorConfigBundle.get("usage.type.unknown")
    }
  }
}
