// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteChildrenRecursively
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.io.path.extension


class NioPathUtilTest : NioPathUtilTestCase() {

  @Test
  fun `test directory find or create`() {
    runBlocking {
      assertNioPath { findOrCreateDirectory("directory") }
        .isEqualsTo { findOrCreateDirectory("directory") }
        .isEqualsTo { findOrCreateDirectory("temp/../directory") }
        .isEqualsTo { findOrCreateDirectory("directory/temp/..") }
        .isExistedDirectory()

      assertNioPath { findOrCreateDirectory("directory/dir") }
        .isEqualsTo { findOrCreateDirectory("directory/dir") }
        .isEqualsTo { findOrCreateDirectory("directory/temp/../dir") }
        .isEqualsTo { findOrCreateDirectory("directory/dir/temp/..") }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file find or create`() {
    runBlocking {
      assertNioPath { findOrCreateFile("file.txt") }
        .isEqualsTo { findOrCreateFile("file.txt") }
        .isEqualsTo { findOrCreateFile("temp/../file.txt") }
        .isExistedFile()

      assertNioPath { findOrCreateFile("directory/file.txt") }
        .isEqualsTo { findOrCreateFile("directory/file.txt") }
        .isEqualsTo { findOrCreateFile("temp/../directory/file.txt") }
        .isEqualsTo { findOrCreateFile("directory/temp/../file.txt") }
        .isExistedFile()
    }
  }

  @Test
  fun `test directory finding and creation`() {
    runBlocking {
      assertNioPath { getResolvedPath("directory") }
        .doesNotExist()
      assertNioPath { createDirectory("directory") }
        .isEqualsTo { getResolvedPath("directory") }
        .isEqualsTo { getResolvedPath("directory/temp/..") }
        .isExistedDirectory()

      assertNioPath { getResolvedPath("directory/dir") }
        .doesNotExist()
      assertNioPath { getResolvedPath("directory/dir/temp") }
        .doesNotExist()
      assertNioPath { createDirectory("directory/dir/temp/..") }
        .isEqualsTo { getResolvedPath("directory/dir") }
        .isEqualsTo { getResolvedPath("directory/dir/temp/..") }
        .isExistedDirectory()
      assertNioPath { getResolvedPath("directory/dir/temp") }
        .doesNotExist()

      assertNioPath { createDirectory("d1/d2/d3/d4") }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file finding and creation`() {
    runBlocking {
      assertNioPath { getResolvedPath("file.txt") }
        .doesNotExist()
      assertNioPath { createFile("file.txt") }
        .isEqualsTo { getResolvedPath("file.txt") }
        .isEqualsTo { getResolvedPath("temp/../file.txt") }
        .isExistedFile()

      assertNioPath { getResolvedPath("directory") }
        .doesNotExist()
      assertNioPath { getResolvedPath("directory/temp") }
        .doesNotExist()
      assertNioPath { createFile("directory/temp/../file.txt") }
        .isEqualsTo { getResolvedPath("directory/file.txt") }
        .isEqualsTo { getResolvedPath("directory/temp/../file.txt") }
        .isExistedFile()
      assertNioPath { getResolvedPath("directory/temp") }
        .doesNotExist()

      assertNioPath { createFile("d1/d2/d3/d4/file.txt") }
        .isExistedFile()
    }
  }

  @Test
  fun `test creation errors`() {
    runBlocking {
      assertNioPath { createFile("file.txt") }
        .isExistedFile()
      assertNioPath { createDirectory("directory") }
        .isExistedDirectory()

      assertNioPath { createFile("file.txt") }
        .isFailedWithException<IOException>("File already exists: .*")
      assertNioPath { createDirectory("directory") }
        .isFailedWithException<IOException>("Directory already exists: .*")

      assertNioPath { findOrCreateFile("directory") }
        .isFailedWithException<IOException>("Expected file instead of directory: .*")
      assertNioPath { findOrCreateDirectory("file.txt") }
        .isFailedWithException<IOException>()

      assertNioPath { createFile("file.txt/file.txt") }
        .isFailedWithException<IOException>()
      assertNioPath { createDirectory("file.txt/directory") }
        .isFailedWithException<IOException>()
    }
  }

  @Test
  fun `test delete`() {
    runBlocking {
      root.createFile("file.txt")
      root.deleteRecursively("file.txt")
      assertNioPath { getResolvedPath("file.txt") }
        .doesNotExist()

      repeat(3) {
        root.createDirectory("directory/file$it.txt")
      }
      root.deleteRecursively("directory")
      assertNioPath { getResolvedPath("directory") }
        .doesNotExist()

      root.createFile("directory/file")
      repeat(3) {
        root.createFile("directory/file$it.txt")
      }
      root.deleteChildrenRecursively("directory") { it.extension == "txt" }
      assertNioPath { getResolvedPath("directory") }
        .isExistedDirectory()
      assertNioPath { getResolvedPath("directory/file") }
        .isExistedFile()
      repeat(3) {
        assertNioPath { getResolvedPath("directory/file$it.txt") }
          .doesNotExist()
      }
      root.deleteChildrenRecursively("directory") { true }
      assertNioPath { getResolvedPath("directory") }
        .isExistedDirectory()
        .isEmptyDirectory()
    }
  }
}