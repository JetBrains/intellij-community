// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.UseVirtualFileToVirtualFileUrlConversionInspection
import org.jetbrains.idea.devkit.inspections.UseVirtualFileUrlToVirtualFileConversionInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables the conversion inspections between `VirtualFile` and `VirtualFileUrl`. The project has the stubs of `VirtualFile` and `VirtualFileManager`. With `addVirtualFileUrlStubs`, it also has the stubs of `VirtualFileUrl` and `VirtualFileUrlManager`.
 */
@JvmOverloads
fun virtualFileUrlConversionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
  addVirtualFileUrlStubs: Boolean = true,
): TestFixture<LightDevKitInspectionFixture> = testFixture("virtualFileUrlConversionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(
    UseVirtualFileToVirtualFileUrlConversionInspection(),
    UseVirtualFileUrlToVirtualFileConversionInspection(),
  )
  fixture.addClass(
    """package com.intellij.openapi.vfs;
      |public abstract class VirtualFile {
      |  public abstract String getUrl();
      |}""".trimMargin()
  )
  fixture.addClass(
    """package com.intellij.openapi.vfs;
      |public abstract class VirtualFileManager {
      |  public abstract VirtualFile findFileByUrl(String url);
      |}""".trimMargin()
  )
  if (addVirtualFileUrlStubs) {
    fixture.addClass(
      """package com.intellij.platform.workspace.storage.url;
        |public interface VirtualFileUrl {
        |  String getUrl();
        |}""".trimMargin()
    )
    fixture.addClass(
      """package com.intellij.platform.workspace.storage.url;
        |public interface VirtualFileUrlManager {
        |  VirtualFileUrl storeAndGet(String url);
        |}""".trimMargin()
    )
  }
  initialized(devKit) {}
}
