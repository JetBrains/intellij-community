// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UseEqualsFixtureKt.useEqualsFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/internal")
@TestApplication
public class UseEqualsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<UseEqualsFixture> useEquals =
    useEqualsFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/internal/");

  @Test
  public void testVirtualFile() {
    useEquals.get().doTest(UseVirtualFileEqualsInspection.class,
           "com/intellij/openapi/vfs/VirtualFile.java");
  }

  @Test
  public void testPluginId() {
    useEquals.get().doTest(UsePluginIdEqualsInspection.class,
           "com/intellij/openapi/extensions/PluginId.java");
  }

  @Test
  public void testPrimitiveTypes() {
    useEquals.get().doTest(UsePrimitiveTypesEqualsInspection.class,
           "com/intellij/psi/PsiPrimitiveType.java",
           "com/intellij/psi/PsiType.java");
  }
}