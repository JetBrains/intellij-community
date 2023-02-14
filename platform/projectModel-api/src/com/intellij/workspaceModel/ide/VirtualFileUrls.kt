// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualFileUrls")
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

/**
 * Returns instance of [VirtualFileUrlManager] corresponding to [project]. 
 * It should be used from Java code only, Kotlin code should use `VirtualFileUrlManager.getInstance(project)` extension function instead.
 */
fun getVirtualFileUrlManager(project: Project): VirtualFileUrlManager = project.service()

/**
 * Returns instance of [VirtualFile] corresponding to this [VirtualFileUrl] or `null` if there is no a file with such URL in the Virtual 
 * File System. 
 * 
 * Usually this property returns an instance cached in a field, so it's cheap. If no value is cached, it'll fall back to use 
 * [VirtualFileManager.findFileByUrl].
 */
val VirtualFileUrl.virtualFile: VirtualFile?
  get() = if (this is VirtualFilePointer) file else VirtualFileManager.getInstance().findFileByUrl(url)

/**
 * Returns instance of [VirtualFileUrl] describing this [VirtualFile]. Note that if URL of this file wasn't registered in [virtualFileManager],
 * and new instance will be created and stored in [virtualFileManager] until the project is closed. So this function should be used only
 * if you're going to store the result in a property of some [com.intellij.workspaceModel.storage.WorkspaceEntity].
 *
 * **Important Note:** method can return different instances of `VirtualFileUrl` for the same `VirtualFile`, e.g. if the file was moved.
 */
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromUrl(this.url)
