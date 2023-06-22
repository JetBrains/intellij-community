// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.*
import com.intellij.testFramework.utils.vfs.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException

class VirtualFileUtilTest : VirtualFileUtilTestCase() {

  @Test
  fun `test directory find or create`() {
    runBlocking {
      assertVirtualFile { writeAction { findOrCreateDirectory("directory") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("directory") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("temp/../directory") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("directory/temp/..") } }
        .isExistedDirectory()

      assertVirtualFile { writeAction { findOrCreateDirectory("directory/dir") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("directory/dir") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("directory/temp/../dir") } }
        .isEqualsTo { writeAction { findOrCreateDirectory("directory/dir/temp/..") } }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file find or create`() {
    runBlocking {
      assertVirtualFile { writeAction { findOrCreateFile("file.txt") } }
        .isEqualsTo { writeAction { findOrCreateFile("file.txt") } }
        .isEqualsTo { writeAction { findOrCreateFile("temp/../file.txt") } }
        .isExistedFile()

      assertVirtualFile { writeAction { findOrCreateFile("directory/file.txt") } }
        .isEqualsTo { writeAction { findOrCreateFile("directory/file.txt") } }
        .isEqualsTo { writeAction { findOrCreateFile("temp/../directory/file.txt") } }
        .isEqualsTo { writeAction { findOrCreateFile("directory/temp/../file.txt") } }
        .isExistedFile()
    }
  }

  @Test
  fun `test directory finding and creation`() {
    runBlocking {
      assertVirtualFile { readAction { findDirectory("directory") } }
        .doesNotExist()
      assertVirtualFile { writeAction { createDirectory("directory") } }
        .isEqualsTo { readAction { findDirectory("directory") } }
        .isEqualsTo { readAction { findDirectory("directory/temp/..") } }
        .isExistedDirectory()

      assertVirtualFile { readAction { findDirectory("directory/dir") } }
        .doesNotExist()
      assertVirtualFile { readAction { findDirectory("directory/dir/temp") } }
        .doesNotExist()
      assertVirtualFile { writeAction { createDirectory("directory/dir/temp/..") } }
        .isEqualsTo { readAction { findDirectory("directory/dir") } }
        .isEqualsTo { readAction { findDirectory("directory/dir/temp/..") } }
        .isExistedDirectory()
      assertVirtualFile { readAction { findDirectory("directory/dir/temp") } }
        .doesNotExist()

      assertVirtualFile { writeAction { createDirectory("d1/d2/d3/d4") } }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file finding and creation`() {
    runBlocking {
      assertVirtualFile { readAction { findFile("file.txt") } }
        .doesNotExist()
      assertVirtualFile { writeAction { createFile("file.txt") } }
        .isEqualsTo { readAction { findFile("file.txt") } }
        .isEqualsTo { readAction { findFile("temp/../file.txt") } }
        .isExistedFile()

      assertVirtualFile { readAction { findDirectory("directory") } }
        .doesNotExist()
      assertVirtualFile { readAction { findDirectory("directory/temp") } }
        .doesNotExist()
      assertVirtualFile { writeAction { createFile("directory/temp/../file.txt") } }
        .isEqualsTo { readAction { findFile("directory/file.txt") } }
        .isEqualsTo { readAction { findFile("directory/temp/../file.txt") } }
        .isExistedFile()
      assertVirtualFile { readAction { findDirectory("directory/temp") } }
        .doesNotExist()

      assertVirtualFile { writeAction { createFile("d1/d2/d3/d4/file.txt") } }
        .isExistedFile()
    }
  }

  @Test
  fun `test creation errors`() {
    runBlocking {
      assertVirtualFile { writeAction { createFile("file.txt") } }
        .isExistedFile()
      assertVirtualFile { writeAction { createDirectory("directory") } }
        .isExistedDirectory()

      assertVirtualFile { writeAction { createFile("file.txt") } }
        .isFailedWithException<IOException>("File already exists: .*")
      assertVirtualFile { writeAction { createDirectory("directory") } }
        .isFailedWithException<IOException>("Directory already exists: .*")

      assertVirtualFile { writeAction { findOrCreateFile("directory") } }
        .isFailedWithException<IOException>("Expected file instead of directory: .*")
      assertVirtualFile { writeAction { findOrCreateDirectory("file.txt") } }
        .isFailedWithException<IOException>("Expected directory instead of file: .*")

      assertVirtualFile { writeAction { createFile("file.txt/file.txt") } }
        .isFailedWithException<IOException>("Expected directory instead of file: .*")
      assertVirtualFile { writeAction { createDirectory("file.txt/directory") } }
        .isFailedWithException<IOException>("Expected directory instead of file: .*")
    }
  }

  @Test
  fun `test nio path and vfs integration`() {
    runBlocking {
      assertNioPath { getResolvedPath("file.txt") }
        .doesNotExist()
      assertNioPath { findOrCreateFile("file.txt") }
        .assertVirtualFile { refreshAndFindVirtualFile() }
        .isExistedFile()
      assertNioPath { findOrCreateDirectory("directory") }
        .assertVirtualFile { refreshAndFindVirtualDirectory() }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test delete`() {
    runBlocking {
      val root = root.refreshAndGetVirtualDirectory()
      writeAction { root.createFile("file.txt") }
      writeAction { root.deleteRecursively("file.txt") }
      assertVirtualFile { readAction { findFile("file.txt") } }
        .doesNotExist()

      repeat(3) {
        writeAction { root.createDirectory("directory/file$it.txt") }
      }
      writeAction { root.deleteRecursively("directory") }
      assertVirtualFile { readAction { findDirectory("directory") } }
        .doesNotExist()

      writeAction { root.createFile("directory/file") }
      repeat(3) {
        writeAction { root.createFile("directory/file$it.txt") }
      }
      writeAction { root.deleteChildrenRecursively("directory") { it.extension == "txt" } }
      assertVirtualFile { readAction { findDirectory("directory") } }
        .isExistedDirectory()
      assertVirtualFile { readAction { findFile("directory/file") } }
        .isExistedFile()
      repeat(3) {
        assertVirtualFile { readAction { findFile("directory/file$it.txt") } }
          .doesNotExist()
      }
      writeAction { root.deleteChildrenRecursively("directory") { true } }
      assertVirtualFile { readAction { findDirectory("directory") } }
        .isExistedDirectory()
        .isEmptyDirectory()
    }
  }
}