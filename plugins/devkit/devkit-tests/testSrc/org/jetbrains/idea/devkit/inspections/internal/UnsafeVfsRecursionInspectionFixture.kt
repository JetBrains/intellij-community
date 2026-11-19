// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates the [LightDevKitInspectionFixture] of the [UnsafeVfsRecursionInspection] tests for one test. Declare it as an instance field.
 *
 * The fixture enables [UnsafeVfsRecursionInspection]. The project has the stubs of `VirtualFile` and `CustomVirtualFile`.
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun unsafeVfsRecursionInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("unsafeVfsRecursionInspectionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.openapi.vfs;
    public abstract class VirtualFile {
    public abstract String getPath();
      public abstract boolean isDirectory();
      public abstract VirtualFile[] getChildren();
    }
    """.trimIndent())
  fixture.addClass("""
    package com.example;
    import com.intellij.openapi.vfs.VirtualFile;
    public abstract class CustomVirtualFile extends VirtualFile {}
    """.trimIndent())
  fixture.enableInspections(UnsafeVfsRecursionInspection())
  initialized(devKit) {}
}
