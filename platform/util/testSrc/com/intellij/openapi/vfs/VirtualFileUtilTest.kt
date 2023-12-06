// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.NioPathAssertion.Companion.assertNioPath
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.VirtualFileAssertion.Companion.assertVirtualFile
import com.intellij.testFramework.utils.vfs.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path

class VirtualFileUtilTest : VirtualFileUtilTestCase() {

  @Test
  fun `test directory find or create`() {
    runBlocking {
      assertVirtualFile { writeAction { root.findOrCreateDirectory("directory") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("directory") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("temp/../directory") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("directory/temp/..") } }
        .isExistedDirectory()

      assertVirtualFile { writeAction { root.findOrCreateDirectory("directory/dir") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("directory/dir") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("directory/temp/../dir") } }
        .isEqualsTo { writeAction { root.findOrCreateDirectory("directory/dir/temp/..") } }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file find or create`() {
    runBlocking {
      assertVirtualFile { writeAction { root.findOrCreateFile("file.txt") } }
        .isEqualsTo { writeAction { root.findOrCreateFile("file.txt") } }
        .isEqualsTo { writeAction { root.findOrCreateFile("temp/../file.txt") } }
        .isExistedFile()

      assertVirtualFile { writeAction { root.findOrCreateFile("directory/file.txt") } }
        .isEqualsTo { writeAction { root.findOrCreateFile("directory/file.txt") } }
        .isEqualsTo { writeAction { root.findOrCreateFile("temp/../directory/file.txt") } }
        .isEqualsTo { writeAction { root.findOrCreateFile("directory/temp/../file.txt") } }
        .isExistedFile()
    }
  }

  @Test
  fun `test directory finding and creation`() {
    runBlocking {
      assertVirtualFile { readAction { root.findDirectory("directory") } }
        .doesNotExist()
      assertVirtualFile { writeAction { root.createDirectory("directory") } }
        .isEqualsTo { readAction { root.findDirectory("directory") } }
        .isEqualsTo { readAction { root.findDirectory("directory/temp/..") } }
        .isExistedDirectory()

      assertVirtualFile { readAction { root.findDirectory("directory/dir") } }
        .doesNotExist()
      assertVirtualFile { readAction { root.findDirectory("directory/dir/temp") } }
        .doesNotExist()
      assertVirtualFile { writeAction { root.createDirectory("directory/dir/temp/..") } }
        .isEqualsTo { readAction { root.findDirectory("directory/dir") } }
        .isEqualsTo { readAction { root.findDirectory("directory/dir/temp/..") } }
        .isExistedDirectory()
      assertVirtualFile { readAction { root.findDirectory("directory/dir/temp") } }
        .doesNotExist()

      assertVirtualFile { writeAction { root.createDirectory("d1/d2/d3/d4") } }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file finding and creation`() {
    runBlocking {
      assertVirtualFile { readAction { root.findFile("file.txt") } }
        .doesNotExist()
      assertVirtualFile { writeAction { root.createFile("file.txt") } }
        .isEqualsTo { readAction { root.findFile("file.txt") } }
        .isEqualsTo { readAction { root.findFile("temp/../file.txt") } }
        .isExistedFile()

      assertVirtualFile { readAction { root.findDirectory("directory") } }
        .doesNotExist()
      assertVirtualFile { readAction { root.findDirectory("directory/temp") } }
        .doesNotExist()
      assertVirtualFile { writeAction { root.createFile("directory/temp/../file.txt") } }
        .isEqualsTo { readAction { root.findFile("directory/file.txt") } }
        .isEqualsTo { readAction { root.findFile("directory/temp/../file.txt") } }
        .isExistedFile()
      assertVirtualFile { readAction { root.findDirectory("directory/temp") } }
        .doesNotExist()

      assertVirtualFile { writeAction { root.createFile("d1/d2/d3/d4/file.txt") } }
        .isExistedFile()
    }
  }

  @Test
  fun `test creation errors`() {
    runBlocking {
      assertVirtualFile { writeAction { root.createFile("file.txt") } }
        .isExistedFile()
      assertVirtualFile { writeAction { root.createDirectory("directory") } }
        .isExistedDirectory()

      assertVirtualFile { writeAction { root.createFile("file.txt") } }
        .isFailedWithException<IOException>("""
          |File already exists: .*/file.txt
          |  basePath = .*
          |  relativePath = file.txt
        """.trimMargin())
      assertVirtualFile { writeAction { root.createDirectory("directory") } }
        .isFailedWithException<IOException>("""
          |Directory already exists: .*/directory
          |  basePath = .*
          |  relativePath = directory
        """.trimMargin())

      assertVirtualFile { writeAction { root.findOrCreateFile("directory") } }
        .isFailedWithException<IOException>("""
          |Expected file instead of directory: .*/directory
          |  basePath = .*
          |  relativePath = directory
        """.trimMargin())
      assertVirtualFile { writeAction { root.findOrCreateDirectory("file.txt") } }
        .isFailedWithException<IOException>("""
          |Expected directory instead of file: .*/file.txt
          |  basePath = .*
          |  relativePath = file.txt
        """.trimMargin())

      assertVirtualFile { writeAction { root.createFile("file.txt/file.txt") } }
        .isFailedWithException<IOException>("""
          |Expected directory instead of file: .*/file.txt
          |  basePath = .*
          |  relativePath = file.txt/file.txt
        """.trimMargin())
      assertVirtualFile { writeAction { root.createDirectory("file.txt/directory") } }
        .isFailedWithException<IOException>("""
          |Expected directory instead of file: .*/file.txt
          |  basePath = .*
          |  relativePath = file.txt/directory
        """.trimMargin())
    }
  }

  @Test
  fun `test nio path and vfs integration`() {
    runBlocking {
      assertNioPath { nioRoot.resolve("file.txt") }
        .doesNotExist()
      assertNioPath { nioRoot.resolve("directory") }
        .doesNotExist()

      assertVirtualFile { nioRoot.resolve("file.txt").refreshAndFindVirtualFile() }
        .doesNotExist()
      assertVirtualFile { nioRoot.resolve("directory").refreshAndFindVirtualDirectory() }
        .doesNotExist()

      assertNioPath { nioRoot.findOrCreateFile("file.txt") }
        .isExistedFile()
      assertNioPath { nioRoot.findOrCreateDirectory("directory") }
        .isExistedDirectory()

      assertVirtualFile { nioRoot.resolve("file.txt").refreshAndFindVirtualFile() }
        .isExistedFile()
      assertVirtualFile { nioRoot.resolve("directory").refreshAndFindVirtualDirectory() }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test delete`() {
    runBlocking {
      writeAction { root.createFile("file.txt") }
      writeAction { root.deleteRecursively("file.txt") }
      assertVirtualFile { readAction { root.findFile("file.txt") } }
        .doesNotExist()

      repeat(3) {
        writeAction { root.createDirectory("directory/file$it.txt") }
      }
      writeAction { root.deleteRecursively("directory") }
      assertVirtualFile { readAction { root.findDirectory("directory") } }
        .doesNotExist()

      writeAction { root.createFile("directory/file") }
      repeat(3) {
        writeAction { root.createFile("directory/file$it.txt") }
      }
      writeAction { root.deleteChildrenRecursively("directory") { it.extension == "txt" } }
      assertVirtualFile { readAction { root.findDirectory("directory") } }
        .isExistedDirectory()
      assertVirtualFile { readAction { root.findFile("directory/file") } }
        .isExistedFile()
      repeat(3) {
        assertVirtualFile { readAction { root.findFile("directory/file$it.txt") } }
          .doesNotExist()
      }
      writeAction { root.deleteChildrenRecursively("directory") { true } }
      assertVirtualFile { readAction { root.findDirectory("directory") } }
        .isExistedDirectory()
        .isEmptyDirectory()
    }
  }

  @Test
  fun `test multi root file systems`() {
    runBlocking {
      writeAction { root.createFile("C:/c_directory/c_file.txt") }
      writeAction { root.createFile("D:/d_directory/d_file.txt") }

      val testFileSystem = MockMultiRootFileSystem(root)
      val root1 = testFileSystem.refreshAndFindFileByPath("C:")!!
      val root2 = testFileSystem.refreshAndFindFileByPath("D:")!!

      assertVirtualFile { readAction { root1.getDirectory("c_directory") } }
        .isNioPathEqualsTo(Path.of("C:/c_directory"))
      assertVirtualFile { writeAction { root1.createFile("c_directory1/c_file.txt") } }
        .isEqualsTo { readAction { root1.getFile("c_directory1/c_file.txt") } }
        .isNioPathEqualsTo(Path.of("C:/c_directory1/c_file.txt"))

      assertVirtualFile { readAction { root2.getDirectory("d_directory") } }
        .isNioPathEqualsTo(Path.of("D:/d_directory"))
      assertVirtualFile { writeAction { root2.createFile("d_directory1/d_file.txt") } }
        .isEqualsTo { readAction { root2.getFile("d_directory1/d_file.txt") } }
        .isNioPathEqualsTo(Path.of("D:/d_directory1/d_file.txt"))
    }
  }
}