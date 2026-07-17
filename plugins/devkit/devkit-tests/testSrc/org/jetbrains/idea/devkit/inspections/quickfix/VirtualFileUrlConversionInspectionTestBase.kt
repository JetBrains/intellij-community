// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.UseVirtualFileToVirtualFileUrlConversionInspection
import org.jetbrains.idea.devkit.inspections.UseVirtualFileUrlToVirtualFileConversionInspection

abstract class VirtualFileUrlConversionInspectionTestBase : LightDevKitInspectionFixTestBase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(
      UseVirtualFileToVirtualFileUrlConversionInspection(),
      UseVirtualFileUrlToVirtualFileConversionInspection(),
    )
    myFixture.addClass(
      """package com.intellij.openapi.vfs;
        |public abstract class VirtualFile {
        |  public abstract String getUrl();
        |}""".trimMargin()
    )
    myFixture.addClass(
      """package com.intellij.openapi.vfs;
        |public abstract class VirtualFileManager {
        |  public abstract VirtualFile findFileByUrl(String url);
        |}""".trimMargin()
    )
    addVirtualFileUrlStubs()
  }

  protected open fun addVirtualFileUrlStubs() {
    myFixture.addClass(
      """package com.intellij.platform.workspace.storage.url;
        |public interface VirtualFileUrl {
        |  String getUrl();
        |}""".trimMargin()
    )
    myFixture.addClass(
      """package com.intellij.platform.workspace.storage.url;
        |public interface VirtualFileUrlManager {
        |  VirtualFileUrl getOrCreateFromUrl(String url);
        |}""".trimMargin()
    )
  }
}
