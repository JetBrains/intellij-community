// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.util.asSafely
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.service.resolve.DECLARATION_ALTERNATIVES
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

class GradleLookupWeigher : LookupElementWeigher("gradleWeigher", true, false) {

  override fun weigh(element: LookupElement, context: WeighingContext): Comparable<*> {
    val completionElement = element.`object`
    val holder =
      completionElement.asSafely<UserDataHolder>()
      ?: completionElement.asSafely<GroovyResolveResult>()?.element
      ?: return 0

    val comparable: Int = element.getUserData(COMPLETION_PRIORITY) ?: holder.getUserData(COMPLETION_PRIORITY) ?: getFallbackCompletionPriority(holder)
    val deprecationMultiplier = if (element.psiElement?.getUserData(DECLARATION_ALTERNATIVES) != null) 0.9 else 1.0
    return (comparable.toDouble() * deprecationMultiplier).toInt()
  }

  private fun getFallbackCompletionPriority(holder: UserDataHolder): Int {
    if (holder !is PsiMember) {
      return 0
    }
    val containingFile = holder.containingFile ?: holder.asSafely<PsiMember>()?.containingClass?.containingFile
    if (containingFile is PsiJavaFile && containingFile.packageName.startsWith("org.gradle")) {
      return DEFAULT_COMPLETION_PRIORITY
    }
    return 0
  }

  companion object {
    @JvmStatic
    fun setGradleCompletionPriority(element: UserDataHolder, priority: Int) {
      element.putUserData(COMPLETION_PRIORITY, priority)
    }

    const val DEFAULT_COMPLETION_PRIORITY : Int = 100

    private val COMPLETION_PRIORITY: Key<Int> = Key.create("grouping priority for gradle completion results")

    @TestOnly
    fun getGradleCompletionPriority(element: UserDataHolder) : Int? = element.getUserData(COMPLETION_PRIORITY)
  }
}