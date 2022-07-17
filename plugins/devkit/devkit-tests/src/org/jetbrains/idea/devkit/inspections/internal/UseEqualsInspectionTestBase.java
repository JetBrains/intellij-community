// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;

@SuppressWarnings("unused")
public abstract class UseEqualsInspectionTestBase extends PluginModuleTestCase {

  public abstract void testVirtualFile();

  public abstract void testPluginId();

  public abstract void testPrimitiveTypes();

  protected void doTest(@NotNull Class<? extends UseEqualsInspectionBase> inspection,
                        @TestDataFile String @NotNull... filePaths) {
    myFixture.enableInspections(inspection);
    myFixture.allowTreeAccessForAllFiles();
    myFixture.testHighlightingAllFiles(true, false, false, filePaths);
  }
}
