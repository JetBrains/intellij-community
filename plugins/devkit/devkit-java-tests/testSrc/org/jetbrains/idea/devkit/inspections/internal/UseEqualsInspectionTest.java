// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/internal")
public class UseEqualsInspectionTest extends UseEqualsInspectionTestBase {

  @Override
  protected @NotNull String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/internal/";
  }

  @Override
  public void testVirtualFile() {
    doTest(UseVirtualFileEqualsInspection.class,
           "com/intellij/openapi/vfs/VirtualFile.java");
  }

  @Override
  public void testPluginId() {
    doTest(UsePluginIdEqualsInspection.class,
           "com/intellij/openapi/extensions/PluginId.java");
  }

  @Override
  public void testPrimitiveTypes() {
    doTest(UsePrimitiveTypesEqualsInspection.class,
           "com/intellij/psi/PsiPrimitiveType.java",
           "com/intellij/psi/PsiType.java");
  }
}