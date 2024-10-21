// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.ArrayUtilRt.EMPTY_STRING_ARRAY
import com.intellij.util.containers.toArray
import com.intellij.util.io.URLUtil
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal class FileContainerDescription(val urls: List<VirtualFileUrl>, private val jarDirectories: List<JarDirectoryDescription>) {
  private val virtualFilePointersList = mutableSetOf<VirtualFilePointer>()
  private val virtualFilePointerManager = VirtualFilePointerManager.getInstance()

  private val cachedFilesList: AtomicReference<Pair<Array<VirtualFile>, Long>> = AtomicReference(Pair(emptyArray(), -1))

  @Volatile
  private var cachedUrlsList: Array<String>? = null

  init {
    urls.forEach { virtualFilePointersList.add(it as VirtualFilePointer) }
    jarDirectories.forEach { virtualFilePointersList.add(it.directoryUrl as VirtualFilePointer) }
  }

  fun isJarDirectory(url: String): Boolean = jarDirectories.any { it.directoryUrl.url == url }
  fun findByUrl(url: String): VirtualFilePointer? = virtualFilePointersList.find { it.url == url }
  fun getList(): List<VirtualFilePointer> = Collections.unmodifiableList(virtualFilePointersList.toList())
  fun getUrls(): Array<String> {
    if (cachedUrlsList == null) {
      cachedUrlsList = virtualFilePointersList.map { it.url }.toArray(EMPTY_STRING_ARRAY)
    }
    return cachedUrlsList!!
  }

  fun getFiles(): Array<VirtualFile> {
    val cachedResults = cachedFilesList.get()
    return if (cachedResults.second == virtualFilePointerManager.modificationCount) cachedResults.first else cacheVirtualFilePointersData()
  }

  private fun cacheVirtualFilePointersData(): Array<VirtualFile> {
    val timestampBefore = virtualFilePointerManager.modificationCount // snapshot at the beginning

    val cachedFiles: MutableList<VirtualFile> = ArrayList(virtualFilePointersList.size)
    val cachedDirectories: MutableList<VirtualFile> = ArrayList(virtualFilePointersList.size / 3)
    var allFilesAreDirs = true
    for (pointer in virtualFilePointersList) {
      if (!pointer.isValid) continue
      val file = pointer.file
      if (file != null) {
        cachedFiles.add(file)
        if (file.isDirectory) {
          cachedDirectories.add(file)
        }
        else {
          allFilesAreDirs = false
        }
      }
    }

    for (jarDirectory in jarDirectories) {
      val virtualFilePointer = jarDirectory.directoryUrl as VirtualFilePointer
      if (!virtualFilePointer.isValid) continue
      val directoryFile = virtualFilePointer.file
      if (directoryFile != null) {
        cachedDirectories.remove(directoryFile)
        if (jarDirectory.recursive) {
          VfsUtilCore.visitChildrenRecursively(directoryFile, object : VirtualFileVisitor<Void?>() {
            override fun visitFile(file: VirtualFile): Boolean {
              if (!file.isDirectory && FileTypeRegistry.getInstance().getFileTypeByFileName(
                  file.nameSequence) === ArchiveFileType.INSTANCE) {
                val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR)
                if (jarRoot != null) {
                  cachedFiles.add(jarRoot)
                  cachedDirectories.add(jarRoot)
                  return false
                }
              }
              return true
            }
          })
        }
        else {
          if (!directoryFile.isValid) continue
          val children = directoryFile.children
          for (file in children) {
            if (!file.isDirectory && FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence) === ArchiveFileType.INSTANCE) {
              val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR)
              if (jarRoot != null) {
                cachedFiles.add(jarRoot)
                cachedDirectories.add(jarRoot)
              }
            }
          }
        }
      }
    }
    val files = if (allFilesAreDirs) VfsUtilCore.toVirtualFileArray(cachedDirectories) else VfsUtilCore.toVirtualFileArray(cachedFiles)

    val timestampAfter = virtualFilePointerManager.modificationCount // snapshot at the end
    if (timestampBefore == timestampAfter) {
      // Sometimes we may overwrite more recent results. This is fine. This should not happen often, cache will be re-calculated on the following query.
      cachedFilesList.set(Pair(files, timestampAfter))
    }
    // else {
    // we don't know what we have calculated just now. This might happen, because  findFileByUrl might load (not yet loaded) children
    // and increment the counter, or because the client didn't hold RA and another VFS event has occurred. Either way, don't cache
    // and don't log an error, because incrementing VFS counter from findFileByUrl is expected (though, not desired) behavior.
    // thisLogger().error("Race detected: fileManager.modificationCount has changed during method invocation. Probably, missing ReadAction?")
    // }
    return files
  }
}

internal data class JarDirectoryDescription(val directoryUrl: VirtualFileUrl, val recursive: Boolean)