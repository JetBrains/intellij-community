// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.config

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiUtil
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.Processor

/**
 * @author Vladislav.Soroka
 */
class GradleImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean {
    return hasUsageOfType(element, null)
  }

  override fun isImplicitRead(element: PsiElement): Boolean {
    return hasUsageOfType(element, UsageType.READ)
  }

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return hasUsageOfType(element, UsageType.WRITE)
  }

  private fun hasUsageOfType(element: PsiElement, usage: UsageType?): Boolean {
    if (element !is PsiMember) return false

    val found = Ref.create(false)
    GradleUseScopeEnlarger.search(element, Processor {

      if (!it.isReferenceTo(element))
        return@Processor true

      if (usage == null) {
        found.set(true)
        return@Processor false
      }

      val refElement = it.element as? PsiExpression ?: return@Processor true

      when (usage) {
        UsageType.READ -> if (PsiUtil.isAccessedForReading(refElement)) {
          found.set(true)
          return@Processor false
        }
        UsageType.WRITE -> if (PsiUtil.isAccessedForWriting(refElement)) {
          found.set(true)
          return@Processor false
        }
      }
      true
    })
    return found.get()
  }
}
