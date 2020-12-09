// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.*

class FileContainerDescription(val urls: List<VirtualFileUrl>, val jarDirectories: List<JarDirectoryDescription>) {
  private val virtualFilePointersList: ConcurrentList<VirtualFilePointer> = ContainerUtil.createConcurrentList()
  private val virtualFilePointerManager = VirtualFilePointerManager.getInstance()
  @Volatile
  private var timestampOfCachedFiles = -1L
  @Volatile
  private var cachedFilesList = Pair(arrayOf<String>(), arrayOf<VirtualFile>())

   init {
     urls.forEach { virtualFilePointersList.addIfAbsent(it as VirtualFilePointer) }
     jarDirectories.forEach { virtualFilePointersList.addIfAbsent(it.directoryUrl as VirtualFilePointer) }
   }

  fun isJarDirectory(url: String): Boolean = jarDirectories.any { it.directoryUrl.url == url }
  fun findByUrl(url: String): VirtualFilePointer? = virtualFilePointersList.find { it.url == url }
  fun getList(): List<VirtualFilePointer> = Collections.unmodifiableList(virtualFilePointersList)
  fun getUrls(): Array<String> = getCachedData().first
  fun getFiles(): Array<VirtualFile> = getCachedData().second

  private fun getCachedData(): Pair<Array<String>, Array<VirtualFile>> {
    val timestamp = timestampOfCachedFiles
    val cachedResults = cachedFilesList
    return if (timestamp == virtualFilePointerManager.modificationCount) cachedResults else  cacheVirtualFilePointersData()
  }

  private fun cacheVirtualFilePointersData(): Pair<Array<String>, Array<VirtualFile>> {
    val cachedUrls: MutableList<String> = ArrayList(virtualFilePointersList.size)
    val cachedFiles: MutableList<VirtualFile> = ArrayList(virtualFilePointersList.size)
    val cachedDirectories: MutableList<VirtualFile> = ArrayList(virtualFilePointersList.size / 3)
    var allFilesAreDirs = true
    for (pointer in virtualFilePointersList) {
      cachedUrls.add(pointer.url)
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
        } else {
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
    val urlsArray = ArrayUtilRt.toStringArray(cachedUrls)
    val files = if (allFilesAreDirs) VfsUtilCore.toVirtualFileArray(cachedDirectories) else VfsUtilCore.toVirtualFileArray(cachedFiles)
    val result = Pair(urlsArray, files)
    cachedFilesList = result
    timestampOfCachedFiles = virtualFilePointerManager.modificationCount
    return result
  }
}

data class JarDirectoryDescription(val directoryUrl: VirtualFileUrl, val recursive: Boolean)