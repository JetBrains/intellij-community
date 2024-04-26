// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Used to mock windows like files system in VirtualFileUtil tests.
 * Note: It may have issues in general usage, because it is needed only for reproducing one specific bug.
 */
internal class MockMultiRootFileSystem(
  private val root: VirtualFile
) : VirtualFileSystem() {

  private val fileSystem: VirtualFileSystem
    get() = root.fileSystem

  private fun VirtualFile.toMockFile(): MockVirtualFile {
    return when (this) {
      is MockVirtualFile -> this
      else -> MockVirtualFile(this)
    }
  }

  private fun VirtualFile.toRealFile(): VirtualFile {
    return when (this) {
      is MockVirtualFile -> file
      else -> this
    }
  }

  private fun getRealRoot(file: VirtualFile): VirtualFile {
    val nioPath = file.toNioPath()
    return checkNotNull(root.children.find { nioPath.startsWith(it.toNioPath()) }) {
      "Cannot find test root for real path $nioPath"
    }
  }

  private fun getRealPath(mockPath: String): String {
    if (mockPath.startsWith("/")) {
      val root = root.children.first()
      return root.path + mockPath
    }
    else {
      val rootName = Path.of(mockPath).first().pathString
      val root = checkNotNull(root.children.find { it.name == rootName }) {
        "Cannot find test root for test path $mockPath"
      }
      return root.path + mockPath.removePrefix(rootName)
    }
  }

  override fun getNioPath(file: VirtualFile): Path? {
    return (file as? MockVirtualFile)?.toNioPath()
  }

  override fun getProtocol(): String {
    return "test"
  }

  override fun findFileByPath(path: String): VirtualFile? {
    return fileSystem.findFileByPath(getRealPath(path))?.toMockFile()
  }

  override fun refresh(asynchronous: Boolean) {
    fileSystem.refresh(asynchronous)
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return fileSystem.refreshAndFindFileByPath(getRealPath(path))?.toMockFile()
  }

  override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
    fileSystem.deleteFile(requestor, vFile.toRealFile())
  }

  override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
    fileSystem.moveFile(requestor, vFile.toRealFile(), newParent.toRealFile())
  }

  override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
    fileSystem.renameFile(requestor, vFile.toRealFile(), newName)
  }

  override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
    return fileSystem.createChildFile(requestor, vDir.toRealFile(), fileName).toMockFile()
  }

  override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
    return fileSystem.createChildDirectory(requestor, vDir.toRealFile(), dirName).toMockFile()
  }

  override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    return fileSystem.copyFile(requestor, virtualFile.toRealFile(), newParent.toRealFile(), copyName).toMockFile()
  }

  override fun isReadOnly(): Boolean {
    return fileSystem.isReadOnly
  }

  override fun addVirtualFileListener(listener: VirtualFileListener) {}

  override fun removeVirtualFileListener(listener: VirtualFileListener) {}

  private inner class MockVirtualFile(
    val file: VirtualFile
  ) : VirtualFile() {

    val root: VirtualFile = getRealRoot(file)

    override fun getName(): String {
      return file.name
    }

    override fun getFileSystem(): MockMultiRootFileSystem {
      return this@MockMultiRootFileSystem
    }

    override fun getUrl(): String {
      return VirtualFileManager.constructUrl(fileSystem.protocol, root.name + path)
    }

    override fun toNioPath(): Path {
      return Path.of(root.name + path)
    }

    override fun getPath(): String {
      return file.path.removePrefix(root.path)
        .ifEmpty { "/" }
    }

    override fun toString(): String {
      return url
    }

    override fun isWritable(): Boolean {
      return file.isWritable
    }

    override fun isDirectory(): Boolean {
      return file.isDirectory
    }

    override fun isValid(): Boolean {
      return file.isValid
    }

    override fun getParent(): VirtualFile? {
      if (root.path == file.path) {
        return null
      }
      return file.parent.toMockFile()
    }

    override fun getChildren(): Array<VirtualFile> {
      return file.children.map { it.toMockFile() }.toTypedArray()
    }

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
      return file.getOutputStream(requestor, newModificationStamp, newTimeStamp)
    }

    override fun contentsToByteArray(): ByteArray {
      return file.contentsToByteArray()
    }

    override fun getTimeStamp(): Long {
      return file.timeStamp
    }

    override fun getLength(): Long {
      return file.length
    }

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
      file.refresh(asynchronous, recursive, postRunnable)
    }

    override fun getInputStream(): InputStream {
      return file.inputStream
    }

    override fun createChildData(requestor: Any?, name: String): VirtualFile {
      return file.createChildData(requestor, name).toMockFile()
    }

    override fun createChildDirectory(requestor: Any?, name: String): VirtualFile {
      return file.createChildDirectory(requestor, name).toMockFile()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as MockVirtualFile

      return file == other.file
    }

    override fun hashCode(): Int {
      return file.hashCode()
    }
  }
}
