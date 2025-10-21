// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractRenameActionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

internal class KotlinRenameActionCommandProvider: AbstractRenameActionCommandProvider() {
  override fun findRenameOffset(offset: Int, psiFile: PsiFile): Int? {
      var currentOffset = offset
      if (currentOffset == 0) return null
      var element = getCommandContext(currentOffset, psiFile) ?: return null
      if (element is PsiWhiteSpace) {
          element = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
          currentOffset = element.textRange.endOffset
      }

      // fun some..(arg: String)..{
      // }..
      // fun some..(arg: String)..=
      // <..> place to call 'rename'
      val method = element.parentOfType<KtFunction>()
      if (method != null &&
          (method.identifyingElement?.textRange?.endOffset == currentOffset ||
                  method.valueParameterList?.textRange?.endOffset == currentOffset ||
                  method.bodyBlockExpression?.textRange?.endOffset == currentOffset)
      ) return method.identifyingElement?.textRange?.endOffset

      val psiClass = element.parentOfType<KtClass>()
      if (psiClass != null && psiClass.getBody()?.rBrace != null && psiClass.getBody()?.rBrace?.textRange?.endOffset == currentOffset) {
          return psiClass.identifyingElement?.textRange?.endOffset
      }
      return offset
  }
}