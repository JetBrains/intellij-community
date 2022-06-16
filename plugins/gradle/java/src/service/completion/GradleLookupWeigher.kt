// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

class GradleLookupWeigher : LookupElementWeigher("gradleWeigher", true, false) {

  override fun weigh(element: LookupElement, context: WeighingContext): Comparable<*> {
    val completionElement = element.`object`
    val holder =
      completionElement.castSafelyTo<UserDataHolder>()
      ?: completionElement.castSafelyTo<GroovyResolveResult>()?.element
      ?: return 0
    return holder.getUserData(COMPLETION_PRIORITY) ?: getFallbackCompletionPriority(holder)
  }

  private fun getFallbackCompletionPriority(holder: UserDataHolder): Int {
    if (holder !is PsiMember) {
      return 0
    }
    val containingFile = holder.containingFile ?: holder.castSafelyTo<PsiMember>()?.containingClass?.containingFile
    if (containingFile is PsiJavaFile && containingFile.packageName.startsWith("org.gradle")) {
      return 1
    }
    return 0
  }

  companion object {
    @JvmStatic
    fun setGradleCompletionPriority(element: PsiElement, priority: Int) {
      element.putUserData(COMPLETION_PRIORITY, priority)
    }

    private val COMPLETION_PRIORITY = Key.create<Int>("grouping priority for gradle completion results")
  }
}