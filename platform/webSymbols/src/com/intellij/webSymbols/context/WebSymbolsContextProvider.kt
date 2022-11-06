// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider

/*
 * DEPRECATION -> @JvmDefault
 */
@Suppress("DEPRECATION")
interface WebSymbolsContextProvider {
  /**
   * Determines whether a particular, parsed file should have a particular web symbols context (like web framework) enabled.
   * Such files will for example have dedicated JS expressions injected. This API serves for a purpose of enabling the
   * support in particular files, when the support should not be provided on a directory level.
   * It is a responsibility of the context provider to cache value if needed.
   */
  @JvmDefault
  fun isEnabled(file: PsiFile): Boolean = false

  /**
   * Determines whether a particular file should have a particular web symbols context (like web framework) enabled.
   * This method is used before creating a PsiFile, so it should not try to use PsiManager to find a PsiFile.
   * This API serves for a purpose of enabling the support in particular files, when the support should not be provided
   * on a directory level. It is a responsibility of the context provider to cache value if needed.
   */
  @JvmDefault
  fun isEnabled(file: VirtualFile, project: Project): Boolean = false

  /**
   * Determines whether files within a particular folder should have a particular web symbols context (like web framework)
   * enabled. Amongst others, html files in such folders might be parsed with dedicated parser, so
   * JS expressions will be part of PSI tress instead of being injected.
   * It is a responsibility of the context provider to include all dependencies of the value.
   * The result is being cached.
   *
   * It is important that result is stable as any change will result in full reload of code insight
   * and clear of all caches.
   *
   * Since there might be many contexts of the same kind within the same project, the result is a proximity score of how
   * far from the parent folder with detected context particular folder is. 0 means that passed
   * {@code directory} is the root of the project.
   *
   * @return {@code null} if not enabled, otherwise a proximity score
   */
  @JvmDefault
  fun isEnabled(project: Project, directory: VirtualFile): CachedValueProvider.Result<Int?> =
    CachedValueProvider.Result(null, ModificationTracker.NEVER_CHANGED)

  /**
   * You can forbid context on a particular file to allow for cooperation between different
   * plugins. This method is used before creating a PsiFile, so it should not try to use PsiManager to find a PsiFile.
   * The result is not cached and therefore the logic should not perform time-consuming tasks, or should cache results
   * on its own.
   *
   * It is important that result is stable as any change will result in full reload of code insight
   * and clear of all caches.
   *
   * You can register a context provider with only a context kind, and it's {@code isForbidden} method will always be called
   * and will allow you to forbid any context of the particular kind.
   */
  @JvmDefault
  fun isForbidden(contextFile: VirtualFile, project: Project): Boolean = false

}