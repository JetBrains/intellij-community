// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.openapi.util.io.NioPathAssertion.Companion.assertNioPath
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteChildrenRecursively
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.extension


class NioPathUtilTest : NioPathUtilTestCase() {

  @Test
  fun `test directory find or create`() {
    runBlocking {
      assertNioPath { root.findOrCreateDirectory("directory") }
        .isEqualsTo { root.findOrCreateDirectory("directory") }
        .isEqualsTo { root.findOrCreateDirectory("temp/../directory") }
        .isEqualsTo { root.findOrCreateDirectory("directory/temp/..") }
        .isExistedDirectory()

      assertNioPath { root.findOrCreateDirectory("directory/dir") }
        .isEqualsTo { root.findOrCreateDirectory("directory/dir") }
        .isEqualsTo { root.findOrCreateDirectory("directory/temp/../dir") }
        .isEqualsTo { root.findOrCreateDirectory("directory/dir/temp/..") }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file find or create`() {
    runBlocking {
      assertNioPath { root.findOrCreateFile("file.txt") }
        .isEqualsTo { root.findOrCreateFile("file.txt") }
        .isEqualsTo { root.findOrCreateFile("temp/../file.txt") }
        .isExistedFile()

      assertNioPath { root.findOrCreateFile("directory/file.txt") }
        .isEqualsTo { root.findOrCreateFile("directory/file.txt") }
        .isEqualsTo { root.findOrCreateFile("temp/../directory/file.txt") }
        .isEqualsTo { root.findOrCreateFile("directory/temp/../file.txt") }
        .isExistedFile()
    }
  }

  @Test
  fun `test directory finding and creation`() {
    runBlocking {
      assertNioPath { root.getResolvedPath("directory") }
        .doesNotExist()
      assertNioPath { root.createDirectory("directory") }
        .isEqualsTo { root.getResolvedPath("directory") }
        .isEqualsTo { root.getResolvedPath("directory/temp/..") }
        .isExistedDirectory()

      assertNioPath { root.getResolvedPath("directory/dir") }
        .doesNotExist()
      assertNioPath { root.getResolvedPath("directory/dir/temp") }
        .doesNotExist()
      assertNioPath { root.createDirectory("directory/dir/temp/..") }
        .isEqualsTo { root.getResolvedPath("directory/dir") }
        .isEqualsTo { root.getResolvedPath("directory/dir/temp/..") }
        .isExistedDirectory()
      assertNioPath { root.getResolvedPath("directory/dir/temp") }
        .doesNotExist()

      assertNioPath { root.createDirectory("d1/d2/d3/d4") }
        .isExistedDirectory()
    }
  }

  @Test
  fun `test file finding and creation`() {
    runBlocking {
      assertNioPath { root.getResolvedPath("file.txt") }
        .doesNotExist()
      assertNioPath { root.createFile("file.txt") }
        .isEqualsTo { root.getResolvedPath("file.txt") }
        .isEqualsTo { root.getResolvedPath("temp/../file.txt") }
        .isExistedFile()

      assertNioPath { root.getResolvedPath("directory") }
        .doesNotExist()
      assertNioPath { root.getResolvedPath("directory/temp") }
        .doesNotExist()
      assertNioPath { root.createFile("directory/temp/../file.txt") }
        .isEqualsTo { root.getResolvedPath("directory/file.txt") }
        .isEqualsTo { root.getResolvedPath("directory/temp/../file.txt") }
        .isExistedFile()
      assertNioPath { root.getResolvedPath("directory/temp") }
        .doesNotExist()

      assertNioPath { root.createFile("d1/d2/d3/d4/file.txt") }
        .isExistedFile()
    }
  }

  @Test
  fun `test creation errors`() {
    runBlocking {
      assertNioPath { root.createFile("file.txt") }
        .isExistedFile()
      assertNioPath { root.createDirectory("directory") }
        .isExistedDirectory()

      assertNioPath { root.createFile("file.txt") }
        .isFailedWithException<IOException>("File already exists: .*")
      assertNioPath { root.createDirectory("directory") }
        .isFailedWithException<IOException>("Directory already exists: .*")

      assertNioPath { root.findOrCreateFile("directory") }
        .isFailedWithException<IOException>("Expected file instead of directory: .*")
      assertNioPath { root.findOrCreateDirectory("file.txt") }
        .isFailedWithException<IOException>()

      assertNioPath { root.createFile("file.txt/file.txt") }
        .isFailedWithException<IOException>()
      assertNioPath { root.createDirectory("file.txt/directory") }
        .isFailedWithException<IOException>()
    }
  }

  @Test
  fun `test delete`() {
    runBlocking {
      root.createFile("file.txt")
      root.deleteRecursively("file.txt")
      assertNioPath { root.getResolvedPath("file.txt") }
        .doesNotExist()

      repeat(3) {
        root.createDirectory("directory/file$it.txt")
      }
      root.deleteRecursively("directory")
      assertNioPath { root.getResolvedPath("directory") }
        .doesNotExist()

      root.createFile("directory/file")
      repeat(3) {
        root.createFile("directory/file$it.txt")
      }
      root.deleteChildrenRecursively("directory") { it.extension == "txt" }
      assertNioPath { root.getResolvedPath("directory") }
        .isExistedDirectory()
      assertNioPath { root.getResolvedPath("directory/file") }
        .isExistedFile()
      repeat(3) {
        assertNioPath { root.getResolvedPath("directory/file$it.txt") }
          .doesNotExist()
      }
      root.deleteChildrenRecursively("directory") { true }
      assertNioPath { root.getResolvedPath("directory") }
        .isExistedDirectory()
        .isEmptyDirectory()
    }
  }

  @ParameterizedTest
  @CsvSource(
    "/1/2/3/4/5, a/b/c,        /1/2/3/4/5, a/b/c",
    "/1/2/3/4/5, ../../a/b/c,  /1/2/3,     a/b/c",
    "/1/2/3/4/5, ../..,        /1/2/3,     ''",
    "/1/2/3/4/5, a/../b,       /1/2/3/4/5, b",
    "/1/2/3/4/5, a/b/..,       /1/2/3/4/5, a",
    "/1/2/3/4/5, .,            /1/2/3/4/5, ''",
    "/1/2/3/4/5, ./a/b,        /1/2/3/4/5, a/b"
  )
  fun `test base and relative paths normalization`(
    basePath: String, relativePath: String,
    expectedBasePath: String, expectedRelativePath: String
  ) {
    val (actualBasePath, actualRelativePath) = Path.of(basePath)
      .relativizeToClosestAncestor(relativePath)
    Assertions.assertEquals(Path.of(expectedBasePath), actualBasePath)
    Assertions.assertEquals(Path.of(expectedRelativePath), actualRelativePath)
  }
}