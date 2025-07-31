// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File
import java.nio.file.Path

val ProjectResolverContext.virtualFileUrlManager: VirtualFileUrlManager
  get() = project.workspaceModel.getVirtualFileUrlManager()

fun ProjectResolverContext.virtualFileUrl(path: Path): VirtualFileUrl {
  return path.toVirtualFileUrl(virtualFileUrlManager)
}

fun ProjectResolverContext.virtualFileUrl(file: File): VirtualFileUrl {
  return virtualFileUrl(file.toPath())
}

fun ProjectResolverContext.virtualFileUrl(path: String): VirtualFileUrl {
  return virtualFileUrl(Path.of(path))
}
