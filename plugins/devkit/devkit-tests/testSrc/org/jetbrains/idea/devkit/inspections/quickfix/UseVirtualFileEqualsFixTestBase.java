// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.internal.UseVirtualFileEqualsInspection;

public abstract class UseVirtualFileEqualsFixTestBase extends LightDevKitInspectionFixTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseVirtualFileEqualsInspection());
    myFixture.addClass("""
      package com.intellij.openapi.vfs;
      public abstract class VirtualFile {}
      """);
  }
}
