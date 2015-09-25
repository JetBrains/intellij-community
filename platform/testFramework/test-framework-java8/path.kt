package com.intellij.testFramework

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

fun Path.exists(): Boolean = Files.exists(this)

fun Path.createDirectories(): Path = Files.createDirectories(this)

fun Path.deleteRecursively(): Path = if (exists()) Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    Files.delete(file)
    return FileVisitResult.CONTINUE
  }

  override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
    Files.delete(dir)
    return FileVisitResult.CONTINUE
  }
}) else this

fun Path.getLastModifiedTime(): FileTime? = Files.getLastModifiedTime(this)

val Path.systemIndependentPath: String
  get() = toString().replace(File.separatorChar, '/')

val Path.parentSystemIndependentPath: String
  get() = parent!!.toString().replace(File.separatorChar, '/')

fun Path.readBytes() = Files.readAllBytes(this)

fun Path.readText() = readBytes().toString(Charsets.UTF_8)

fun VirtualFile.writeChild(relativePath: String, data: String) = VfsTestUtil.createFile(this, relativePath, data)

fun Path.writeChild(relativePath: String, data: ByteArray) = resolve(relativePath).write(data)

fun Path.writeChild(relativePath: String, data: String) = writeChild(relativePath, data.toByteArray())

fun Path.write(data: ByteArray): Path {
  parent?.createDirectories()
  return Files.write(this, data)
}

fun Path.isDirectory() = Files.isDirectory(this)

fun Path.isFile() = Files.isRegularFile(this)

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createFile() {
  parent?.createDirectories()
  Files.createFile(this)
}

fun Path.refreshVfs() {
  LocalFileSystem.getInstance()?.let { fs ->
    // If a temp directory is reused from some previous test run, there might be cached children in its VFS. Ensure they're removed.
    val virtualFile = fs.findFileByPath(systemIndependentPath)
    if (virtualFile != null) {
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    }
  }
}