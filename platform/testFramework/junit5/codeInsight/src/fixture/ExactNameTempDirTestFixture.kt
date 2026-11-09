// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path

/**
 * A temporary directory that keeps the exact file names, as the light temporary directory of a JUnit 3 test does.
 *
 * `configureByText` and `createFile` create the file at [tempPath] with the given name.
 * The default [TempDirTestFixtureImpl] adds a unique suffix to the name, for example `X___123.java`.
 * A second file with the same name replaces the first one.
 * The fixture does not delete [tempPath]. The owner of the directory deletes its files.
 */
@TestOnly
class ExactNameTempDirTestFixture(private val tempPath: Path) : TempDirTestFixtureImpl() {
  override fun doCreateTempDirectory(): Path = tempPath

  override fun deleteOnTearDown(): Boolean = false

  override fun createTempFile(path: String): Path {
    val file = tempPath.resolve(path)
    Files.createDirectories(file.parent)
    if (!Files.exists(file)) {
      Files.createFile(file)
    }
    VfsRootAccess.allowRootAccess(testRootDisposable, file.toString())
    return file
  }
}
