// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.ContextFactory
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.access
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Implements the parts of [ContextFactory] that are the same on both sides. A side implements only the [file] overload
 * for a [com.intellij.openapi.vfs.VirtualFile], because only that side knows its content roots.
 *
 * The subclasses are `BackendSettingsContextFactory` in the backend module and `FrontendSettingsContextFactory` in
 * the remote-dev frontend split module.
 */
abstract class BaseSettingsContextFactory : ContextFactory {
  override fun app(): SettingsContext = AppSettingsContext

  override fun project(project: Project): SettingsContext = ProjectSettingsContext(project)

  @RequiresReadLock
  override fun file(psiFile: PsiFile): SettingsContext {
    val base = psiFile.virtualFile?.let { file(it, psiFile.project) } ?: project(psiFile.project)
    return base.access { language = psiFile.language }
  }

  /**
   * Rebuilds the context of [identity]. It inverts the identity layouts in `SettingsContexts.kt`
   * ([APP_CONTEXT_ID], [PROJECT_CONTEXT_ID], [FILE_CONTEXT_ID]). It is pure string math with no VFS access and no read
   * lock: a file context rebuilds from its stored root and relative path. `split(limit = 4)` keeps the relative path
   * as the remainder, so a `|` in the path does not break it. The relative path is empty for a content root.
   *
   * @param identity the [SettingsContext.identity] of the context to rebuild
   * @return the context, or `null` for a malformed identity, which is also logged as an error
   */
  override fun deserialize(identity: String): SettingsContext? {
    val parts = identity.split('|', limit = 4)
    return when (parts[0]) {
      APP_CONTEXT_ID -> if (parts.size == 1) AppSettingsContext else malformed(identity)
      PROJECT_CONTEXT_ID -> if (parts.size != 2 || parts[1].isEmpty()) malformed(identity) else ProjectSettingsContext(projectId = parts[1])
      FILE_CONTEXT_ID -> {
        if (parts.size != 4 || parts[1].isEmpty() || parts[2].isEmpty()) return malformed(identity)
        VirtualFileSettingsContext(projectId = parts[1],
                                   rootUrl = parts[2],
                                   relativePath = parts[3])
      }
      else -> malformed(identity)
    }
  }

  /** Logs an error for the malformed [identity] and returns `null`. */
  private fun malformed(identity: String): SettingsContext? {
    LOG.error("Malformed settings context identity '$identity'")
    return null
  }

  private companion object {
    private val LOG = logger<BaseSettingsContextFactory>()
  }
}
