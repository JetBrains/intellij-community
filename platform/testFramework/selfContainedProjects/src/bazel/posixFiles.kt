// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.selfContainedProjects.bazel

import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import kotlin.io.path.exists

/** Adds the owner-execute permission on a POSIX file system; a zip strips the bit. Does nothing elsewhere. */
internal fun setExecutable(file: Path) {
  addPermission(file, PosixFilePermission.OWNER_EXECUTE)
}

private fun addPermission(path: Path, permission: PosixFilePermission) {
  val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java) ?: return
  val permissions = view.readAttributes().permissions()
  if (permissions.add(permission)) {
    view.setPermissions(permissions)
  }
}

/** Copies [source] into [target] through symlinks, because under `bazel test` a fixture is a runfiles symlink farm. */
internal fun copyTree(source: Path, target: Path) {
  Files.walkFileTree(source, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(target.resolve(source.relativize(dir).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING)
      return FileVisitResult.CONTINUE
    }
  })
}

/**
 * Deletes [root] with everything below it. Bazel marks its output files and their directories read-only, so each
 * directory gets the owner-write permission back before its entries go.
 */
internal fun deleteTree(root: Path) {
  if (!root.exists()) return
  Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      addPermission(dir, PosixFilePermission.OWNER_WRITE)
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.delete(file)
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, e: IOException?): FileVisitResult {
      if (e != null) throw e
      Files.delete(dir)
      return FileVisitResult.CONTINUE
    }
  })
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun sha256Hex(file: Path): String {
  val digest = MessageDigest.getInstance("SHA-256")
  Files.newInputStream(file).use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
