// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus


private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"

private val EP_NAME: ExtensionPointName<CancellationCheckProvider> = create("DevKit.lang.cancellationCheckProvider")

internal object CancellationCheckProviders : LanguageExtension<CancellationCheckProvider>(EP_NAME.name)


/**
 * Provides the right cancellation check based on the context
 * (see [com.intellij.openapi.progress.ProgressManager.checkCanceled], [com.intellij.openapi.progress.checkCancelled])
 * and checks expressions for cancellation check calls.
 */
@IntellijInternalApi
@ApiStatus.Internal
interface CancellationCheckProvider {

  @RequiresReadLock
  @RequiresBackgroundThread
  fun findCancellationCheckCall(element: PsiElement): String

  @RequiresReadLock
  @RequiresBackgroundThread
  fun isCancellationCheckCall(element: PsiElement, cancellationCheckFqn: String): Boolean

}


internal class JavaCancellationCheckProvider : CancellationCheckProvider {

  override fun findCancellationCheckCall(element: PsiElement): String {
    return PROGRESS_MANAGER_CHECKED_CANCELED
  }

  override fun isCancellationCheckCall(element: PsiElement, cancellationCheckFqn: String): Boolean {
    val resolvedMethod = (element as? PsiMethodCallExpression)?.resolveMethod() ?: return false
    return PsiUtil.getMemberQualifiedName(resolvedMethod) == cancellationCheckFqn
  }
}
