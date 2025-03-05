// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl

import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.util.ThreeState
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.Converter
import com.intellij.util.xml.CustomReferenceConverter
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.references.ActionOrGroupIdReference

internal open class ActionOrGroupReferencingConverter : Converter<String>(), CustomReferenceConverter<String> {

  //<editor-fold desc="Dummy Converter implementation for ExtensionDomExtender">

  override fun fromString(s: @NonNls String?, context: ConvertContext): String? {
    return s
  }

  override fun toString(s: String?, context: ConvertContext): String? {
    return s
  }
  // </editor-fold>

  override fun createReferences(value: GenericDomValue<String?>, element: PsiElement, context: ConvertContext): Array<out PsiReference?> {
    return arrayOf(ActionOrGroupIdReference(element, ElementManipulators.getValueTextRange(element), value.stringValue, isAction(), true))
  }

  protected open fun isAction(): ThreeState = ThreeState.UNSURE

  internal class OnlyGroups : ActionOrGroupReferencingConverter() {
    override fun isAction(): ThreeState = ThreeState.NO
  }

  internal class OnlyActions : ActionOrGroupReferencingConverter() {
    override fun isAction(): ThreeState = ThreeState.YES
  }
}
