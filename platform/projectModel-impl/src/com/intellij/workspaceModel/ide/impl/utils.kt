package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

/**
 * If you're wondering why the naming of these methods is so different  and what's the distinction
 * between them, beside the fact that they return different types of object. Then we made a right
 * choice for naming and method's documentation is for you.
 */


/**
 * Most of the time calling of this property will return cached `VirtualFile` and at this time it will be as
 * cheap as getting property value. If cache is empty or invalid the call will be redirected  into the
 * `VirtualFileManager` for search.
 */
val VirtualFileUrl.virtualFile: VirtualFile?
  get() = if (this is VirtualFileUrlBridge) file else VirtualFileManager.getInstance().findFileByUrl(url)

@Deprecated("Use VirtualFileUrl#virtualFile extension property instead",
            ReplaceWith("virtualFile", imports = ["com.intellij.workspaceModel.ide.impl.virtualFile"]))
fun VirtualFileUrl.toVirtualFile(): VirtualFile? = this.virtualFile

/**
 * You should be aware that calling this method will return the instance of `VirtualFileUrl` which path stores in project level service as
 * tree. Even if the path to which it's pointed is already removed the data will be there until the project close. The call of this method
 * actually walk through the tree and check if the nodes from which the path consist exists and if yes the wrapper for the last node will
 * be returned. If something is missed it'll be added to the tree.
 *
 * **Important Note:** method can return different instances of `VirtualFileUrl` for the same `VirtualFile` e.g. then the file was moved.
 */
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromUrl(this.url)
