// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.internal.UsePluginIdEqualsInspection;

public abstract class UsePluginIdEqualsFixTestBase extends LightDevKitInspectionFixTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
      package com.intellij.openapi.extensions;
      public final class PluginId {
        public static PluginId getId(String idString) {return null;}
      }
      """);
    myFixture.enableInspections(new UsePluginIdEqualsInspection());
  }
}
