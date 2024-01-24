// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("VirtualFileUrlManagerUtil")

package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

/**
 * Returns instance of [VirtualFileUrlManager] which should be used to create [VirtualFileUrl] instances to be stored in
 * [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] entities.
 */
//fun VirtualFileUrlManager.Companion.getInstance(project: Project): VirtualFileUrlManager = project.service()

/**
 * Returns instance of [VirtualFileUrlManager] which should be used to create [VirtualFileUrl] instances to be stored in entities added in
 * the global application-level storage. 
 * It's important not to use this function for entities stored in the main [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] 
 * storage, because this would create a memory leak: these instances won't be removed when the project is closed. 
 */
@ApiStatus.Internal
fun VirtualFileUrlManager.Companion.getGlobalInstance(): VirtualFileUrlManager = ApplicationManager.getApplication().service()

fun VirtualFileUrl.isEqualOrParentOf(other: VirtualFileUrl): Boolean = FileUtil.startsWith(other.url.removeSuffix("/"), url.removeSuffix("/"))

fun VirtualFileUrl.toPath(): Path = Path.of(JpsPathUtil.urlToPath(url))