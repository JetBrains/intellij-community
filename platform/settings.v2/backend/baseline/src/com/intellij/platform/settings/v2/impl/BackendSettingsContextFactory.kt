// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Builds the file contexts in the monolith and on the backend, over the roots of [SettingsRootsCache]. Its frontend
 * twin is `FrontendSettingsContextFactory` in `intellij.platform.settings.v2.frontend.split`. Both anchor a file on
 * the same root url (ADR 0127).
 */
internal class BackendSettingsContextFactory : BaseSettingsContextFactory() {
  @RequiresReadLock
  override fun file(virtualFile: VirtualFile, project: Project): SettingsContext {
    val file = nearestPersistentFile(virtualFile) ?: return project(project)
    // A file under no content root resolves at the project (ADR 0125).
    val root = SettingsRootsCache.getInstance(project).rootFor(file) ?: return project(project)
    return VirtualFileSettingsContext.of(file, root, project)
  }
}
