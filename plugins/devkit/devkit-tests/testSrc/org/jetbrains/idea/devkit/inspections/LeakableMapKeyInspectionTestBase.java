// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

public abstract class LeakableMapKeyInspectionTestBase extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.lang; public abstract class Language {}");
    myFixture.addClass("package com.intellij.openapi.fileTypes; public interface FileType {}");
    myFixture.addClass("package com.intellij.openapi.fileTypes; public abstract class LanguageFileType implements FileType {}");

    myFixture.enableInspections(LeakableMapKeyInspection.class);
  }

  public abstract void testHighlighting();
}
