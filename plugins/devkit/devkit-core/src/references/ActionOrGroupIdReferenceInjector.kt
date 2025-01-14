// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.injection.ReferenceInjector
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.toUElement

internal class ActionOrGroupIdReferenceInjector : ReferenceInjector() {

  override fun getReferences(element: PsiElement, context: ProcessingContext, range: TextRange): Array<out PsiReference?> {
    val uElement = element.toUElement(UInjectionHost::class.java)
    val id = uElement?.evaluateString() ?: ElementManipulators.getValueText(element)
    return arrayOf(ActionOrGroupIdReference(element, range, id, ThreeState.UNSURE))
  }

  override fun getId(): String {
    return "devkit-action-id"
  }

  override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return DevKitBundle.message("action.or.group.reference")
  }

}