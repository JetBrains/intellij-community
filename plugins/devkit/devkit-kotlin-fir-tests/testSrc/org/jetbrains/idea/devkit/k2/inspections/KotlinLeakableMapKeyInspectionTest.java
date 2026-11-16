// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.LeakableMapKeyFixtureKt.leakableMapKeyFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/leakableMapKey")
@TestApplication
public class KotlinLeakableMapKeyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> leakableMapKey =
    leakableMapKeyFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/leakableMapKey");

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = leakableMapKey.get().getFixture();
    myFixture.testHighlighting("Service.kt");
  }
}
