// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.PsiElement

private val EP_NAME: ExtensionPointName<CancellationCheckProvider> = create("DevKit.lang.cancellationCheckProvider")
internal object CancellationCheckProviders : LanguageExtension<CancellationCheckProvider>(EP_NAME.name)

private const val CANCELLATION_CHECK_BLOCKING_FQN = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val CANCELLATION_CHECK_SUSPENDING_FQN = "com.intellij.openapi.progress.CoroutinesKt.checkCancelled"

/**
 * Provides the right cancellation check based on the context
 * (see [com.intellij.openapi.progress.ProgressManager.checkCanceled],
 * [com.intellij.openapi.progress.checkCancelled]).
 */
interface CancellationCheckProvider {

  enum class Context {
    BLOCKING, SUSPENDING
  }

  fun findCancellationCheckFqn(element: PsiElement): String {
    return when (findContext(element)) {
      Context.BLOCKING -> CANCELLATION_CHECK_BLOCKING_FQN
      Context.SUSPENDING -> CANCELLATION_CHECK_SUSPENDING_FQN
    }
  }

  fun findContext(element: PsiElement): Context

}


class JavaCancellationCheckProvider : CancellationCheckProvider {

  override fun findContext(element: PsiElement) = CancellationCheckProvider.Context.BLOCKING

}
