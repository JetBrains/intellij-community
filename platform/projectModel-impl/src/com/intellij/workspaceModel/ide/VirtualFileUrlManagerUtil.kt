// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("VirtualFileUrlManagerUtil")

package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

/**
 * This method was extracted from [VirtualFileUrlManager] because of dependency management. Storage
 * should have as many dependencies as possible and there is no dependency to intellij.platform.core module.
 * That's why this method was declared here, where service was registered.
 */
fun VirtualFileUrlManager.Companion.getInstance(project: Project): VirtualFileUrlManager = project.service()

fun VirtualFileUrl.isEqualOrParentOf(other: VirtualFileUrl): Boolean = FileUtil.startsWith(other.urlWithoutTrailingSlash, urlWithoutTrailingSlash)

fun VirtualFileUrl.toPath(): Path = Path.of(JpsPathUtil.urlToPath(url))