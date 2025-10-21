// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject

import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

private val EP = LanguageExtension<FallbackInjectorSuppressor>("org.intellij.intelliLang.fallbackInjectorSuppressor")

@ApiStatus.Internal
fun isSuppressedFor(contextElement: PsiElement): Boolean = EP.allForLanguage(contextElement.language).any { it.isSuppressedFor(contextElement) }

/**
 * This extension allows you to suppress injections by [DefaultLanguageInjectionPerformer] in [DefaultLanguageInjector].
 *
 * Possible use case:
 *
 * You have a complex injector on the backend of your language support that cannot be moved to the frontend because it relies on backend
 * functionality, such as resolution. Without suppression, the backend may inject an element using the complex injector, while the frontend
 * injects the same element using a generic injector, resulting in inconsistent documents on both sides. Performing actions on such a pair
 * of documents may lead to unpredictable side effects, such as caret jumping, inconsistent changes, or rollbacks.
 *
 * To address this, you should implement a suppressor on the frontend to prevent the element from being injected, delegating all injection
 * tasks to the backend. Note that this approach may introduce latency depending on network conditions.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface FallbackInjectorSuppressor {
  fun isSuppressedFor(contextElement: PsiElement): Boolean
}