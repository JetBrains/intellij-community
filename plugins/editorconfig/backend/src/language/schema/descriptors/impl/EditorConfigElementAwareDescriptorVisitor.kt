// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors.impl

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.editorconfig.language.psi.EditorConfigOptionValuePair
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptorVisitor

abstract class EditorConfigElementAwareDescriptorVisitor : EditorConfigDescriptorVisitor {
  protected abstract val element: EditorConfigDescribableElement

  override fun visitPair(pair: EditorConfigPairDescriptor): Unit =
    handleRelativeToPair(pair::first, pair::second).accept(this)

  private fun isInsertingPairFirst(): Boolean {
    val psiPair = element.parentOfType<EditorConfigOptionValuePair>(withSelf = true) ?: return true
    val leftIdentifiers = PsiTreeUtil.findChildrenOfAnyType(psiPair.first, false, element::class.java)
    return element in leftIdentifiers
  }

  private fun isInsertingPairSecond(): Boolean {
    val psiPair = element.parentOfType<EditorConfigOptionValuePair>(withSelf = true)
    psiPair ?: return false
    val rightIdentifiers = PsiTreeUtil.findChildrenOfAnyType(psiPair.second, false, element::class.java)
    return element in rightIdentifiers
  }

  private fun <R> handleRelativeToPair(
    ifFirst: () -> R,
    ifSecond: () -> R,
    ifError: () -> R = { throw IllegalStateException() }
  ) = when {
    isInsertingPairFirst() -> ifFirst()
    isInsertingPairSecond() -> ifSecond()
    else -> ifError()
  }
}
