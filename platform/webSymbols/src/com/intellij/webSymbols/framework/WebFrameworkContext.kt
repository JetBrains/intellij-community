// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.webSymbols.framework.impl.WebFrameworkExtension

interface WebFrameworkContext {
  /**
   * Determines whether a particular, parsed file should have a particular web framework support enabled.
   * Such files will for example have dedicated JS expressions injected. This API serves for a purpose of enabling the
   * support in particular files, when the support should not be provided on a directory level.
   * It is a responsibility of the context provider to cache value if needed.
   */
  @JvmDefault
  fun isEnabled(file: PsiFile): Boolean = false

  /**
   * Determines whether a particular file should have a particular web framework support enabled.
   * This method is used before creating a PsiFile, so it should not try to use PsiManager to find a PsiFile.
   * This API serves for a purpose of enabling the support in particular files, when the support should not be provided
   * on a directory level. It is a responsibility of the context provider to cache value if needed.
   */
  @JvmDefault
  fun isEnabled(file: VirtualFile, project: Project): Boolean = false

  /**
   * Determines whether files within a particular folder should have a particular web framework support
   * enabled. Amongst others, html files in such folders will be parsed with dedicated parser, so
   * JS expressions will be part of PSI tress instead of being injected.
   * It is a responsibility of the context provider to include all dependencies of the value.
   * The result is being cached.
   *
   * It is important that result is stable as any change will result in full reload of code insight
   * and clear of all caches.
   *
   * Since there might be many frameworks within the same project, the result is a proximity score of how
   * far from the parent folder with detected framework support particular folder is. 0 means that passed
   * {@code directory} is the root of the framework project.
   *
   * @return {@code null} if not enabled, otherwise a proximity score
   */
  @JvmDefault
  fun isEnabled(directory: PsiDirectory): CachedValueProvider.Result<Int?> =
    CachedValueProvider.Result(null, ModificationTracker.NEVER_CHANGED)

  /**
   * You can forbid context on a particular file to allow for cooperation between different
   * plugins. This method is used before creating a PsiFile, so it should not try to use PsiManager to find a PsiFile.
   * The result is not cached and therefore the logic should not perform time consuming tasks, or should cache results
   * on it's own.
   *
   * It is important that result is stable as any change will result in full reload of code insight
   * and clear of all caches.
   *
   * You can register a context provider with framework 'any' and it's {@code isForbidden} method will always be called
   * and will allow you to forbid any framework context.
   */
  @JvmDefault
  fun isForbidden(contextFile: VirtualFile, project: Project): Boolean = false

  companion object {
    val WEB_FRAMEWORK_CONTEXT_EP = WebFrameworkExtension<WebFrameworkContext>("com.intellij.javascript.web.context")
  }
}
