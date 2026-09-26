// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class UnsafeVfsRecursionInspectionTestBase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
      package com.intellij.openapi.vfs;
      public abstract class VirtualFile {
      public abstract String getPath();
        public abstract boolean isDirectory();
        public abstract VirtualFile[] getChildren();
      }
      """);
    myFixture.addClass("""
      package com.example;
      import com.intellij.openapi.vfs.VirtualFile;
      public abstract class CustomVirtualFile extends VirtualFile {}
      """);
    myFixture.enableInspections(new UnsafeVfsRecursionInspection());
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
