// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md
// @spec ../../../../../../../../spec/frontend-file-contexts.spec.md

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.platform.settings.v2.ContextFactory
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.access
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Builds the settings contexts in every product mode. The modes differ only in the roots of [fileContextRootUrls].
 */
internal class SettingsContextFactory : ContextFactory {
  override fun app(): SettingsContext = AppSettingsContext

  override fun project(project: Project): SettingsContext = ProjectSettingsContext(project)

  /**
   * Builds the context of the persistent file of [virtualFile] (see [nearestPersistentFile]). The file matches a root
   * of [fileContextRootUrls] by path, because a frontend file has another protocol than the backend root urls. The
   * context keeps the root url as the provider gave it, so both sides anchor on the same url (ADR 0127).
   *
   * A file of an [ArchiveFileSystem], such as a jar entry, can lie at a path under a root. It belongs to no content
   * root, so it gets the project context, as a file under no root does (ADR 0125).
   *
   * @param virtualFile the file to build the context for
   * @param project the project that holds [virtualFile]
   * @return the file context, or the project context when no root holds the file
   */
  @RequiresReadLock
  override fun file(virtualFile: VirtualFile, project: Project): SettingsContext {
    val file = nearestPersistentFile(virtualFile) ?: return project(project)
    if (file.fileSystem is ArchiveFileSystem) return project(project)
    val path = file.path
    val (rootUrl, relativePath) = fileContextRootUrls(project).firstNotNullOfOrNull { rootUrl ->
      path.pathUnder(rootUrl.urlPath())?.let { rootUrl to it }
    } ?: return project(project)
    return VirtualFileSettingsContext(projectId = project.locationHash, rootUrl = rootUrl, relativePath = relativePath)
  }

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
    private val LOG = logger<SettingsContextFactory>()
  }
}

/** Returns the path of this url. The two sides share the path, because a frontend reaches the same path over its own protocol. */
private fun String.urlPath(): String = substringAfter("://")

/**
 * Returns the part of this path under [rootPath].
 *
 * @return `""` when this path is [rootPath], the rest after `"$rootPath/"` when it lies under it, or `null` otherwise
 */
private fun String.pathUnder(rootPath: String): String? = when {
  this == rootPath -> ""
  startsWith("$rootPath/") -> substring(rootPath.length + 1)
  else -> null
}
