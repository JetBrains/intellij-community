// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

class GradleLookupWeigher : LookupElementWeigher("gradleInPlaceWeigher", true, false) {
  override fun weigh(element: LookupElement, context: WeighingContext): Comparable<*> {
    val completionElement = element.`object`
    val holder = completionElement.castSafelyTo<UserDataHolder>() ?: completionElement.castSafelyTo<GroovyResolveResult>()?.element ?: return 0
    val data = holder.getUserData(COMPLETION_PRIORITY)
    return data ?: 0
  }

  companion object {
    @JvmStatic
    fun setGradleCompletionPriority(element: PsiElement, priority: Int) {
      element.putUserData(COMPLETION_PRIORITY, priority)
    }

    private val COMPLETION_PRIORITY = Key.create<Int>("grouping priority for gradle completion results")
  }
}