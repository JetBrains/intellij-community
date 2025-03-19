// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor

private class EditorConfigFindUsagesProvider : FindUsagesProvider {
  override fun getWordsScanner(): WordsScanner = EditorConfigWordScanner()
  override fun canFindUsagesFor(psiElement: PsiElement) = psiElement is EditorConfigDescribableElement
  override fun getHelpId(psiElement: PsiElement): String = HelpID.FIND_OTHER_USAGES

  override fun getType(element: PsiElement): String {
    if (element is EditorConfigFlatOptionKey) {
      return EditorConfigBundle.get("usage.type.option.key", element.text, element.section.header.text)
    }

    val describable = element as? EditorConfigDescribableElement
    return when (describable?.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor,
      is EditorConfigReferenceDescriptor -> EditorConfigBundle.get(
        "usage.type.identifier",
        describable.text,
        describable.section.header.text
      )
      is EditorConfigConstantDescriptor -> EditorConfigBundle.get(
        "usage.type.constant",
        describable.text,
        describable.section.header.text
      )
      else -> EditorConfigBundle.get("usage.type.unknown")
    }
  }

  override fun getDescriptiveName(element: PsiElement): String {
    if (element is EditorConfigFlatOptionKey) {
      return EditorConfigBundle.get("usage.descriptive.name.site", element.text, element.declarationSite)
    }

    val describable = element as? EditorConfigDescribableElement
    return when (describable?.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor,
      is EditorConfigReferenceDescriptor,
      is EditorConfigConstantDescriptor ->
        EditorConfigBundle.get("usage.descriptive.name.site", describable.text, describable.declarationSite)

      else -> EditorConfigBundle.get("usage.descriptive.name.unknown")
    }
  }

  override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
    if (element is EditorConfigFlatOptionKey) {
      return EditorConfigBundle.get("usage.node.text", element.option.text, element.section.header.text)
    }

    val describable = element as? EditorConfigDescribableElement
    return when (describable?.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor,
      is EditorConfigReferenceDescriptor,
      is EditorConfigConstantDescriptor ->
        return EditorConfigBundle.get("usage.node.text", describable.option.text, describable.section.header.text)

      else -> EditorConfigBundle.get("usage.node.unknown")
    }
  }
}
