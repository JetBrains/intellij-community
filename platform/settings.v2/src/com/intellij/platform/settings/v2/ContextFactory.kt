// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Builds [SettingsContext] instances and their [parentContext][SettingsContext.parentContext] chains. It is in the
 * API module, so the [SettingsContext] factories can delegate to it. API consumers use those factories, not this
 * interface.
 *
 * Each side of the product registers its own implementation as an application service.
 * `BackendSettingsContextFactory` in `intellij.platform.settings.v2.backend` serves the monolith and the backend.
 * `FrontendSettingsContextFactory` in `intellij.platform.settings.v2.frontend.split` serves the frontend.
 */
@Internal
interface ContextFactory {
  /** Builds the application-level context. Backs [SettingsContext.app]. */
  fun app(): SettingsContext

  /** Builds a project-level context for [project]. Backs [SettingsContext.project]. */
  fun project(project: Project): SettingsContext

  /** Builds a file-level context for [virtualFile] within [project]. Backs [SettingsContext.file]. */
  @RequiresReadLock
  fun file(virtualFile: VirtualFile, project: Project): SettingsContext

  /** Builds a file-level context for [psiFile], which carries its own project. Backs [SettingsContext.file]. */
  @RequiresReadLock
  fun file(psiFile: PsiFile): SettingsContext

  /**
   * Rebuilds the context whose [SettingsContext.identity] is [identity]. This is the inverse of
   * [SettingsContext.identity]. The vault uses it to restore the context of a layer from the snapshot cache, with no
   * knowledge of the context types. It only parses the string, so it touches no VFS and needs no read action. The
   * project and the file resolve lazily on use.
   *
   * @param identity the [SettingsContext.identity] of the context to rebuild
   * @return the context, or `null` when [identity] is malformed, for example in a corrupt cache
   */
  fun deserialize(identity: String): SettingsContext?

  companion object {
    fun getInstance(): ContextFactory = service<ContextFactory>()
  }
}
