// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.idea.devkit.inspections.LeakableMapKeyFixtureKt.leakableMapKeyFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestDataPath("$CONTENT_ROOT/testData/inspections/leakableMapKey")
@TestApplication
public class LeakableMapKeyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> leakableMapKey =
    leakableMapKeyFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/leakableMapKey");

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = leakableMapKey.get().getFixture();
    myFixture.testHighlighting("Service.java");
  }

  @Test
  public void testReplaceWithSuperString() {
    LightJavaCodeInsightTestFixture myFixture = leakableMapKey.get().getFixture();
    List<IntentionAction> quickFixes = myFixture.getAllQuickFixes("Service2.java");
    assertEquals(2, quickFixes.size());

    myFixture.launchAction(quickFixes.get(0));
    myFixture.checkResultByFile("Service2.java", "Service2_withSuperString.java", true);
  }

  @Test
  public void testReplaceWithString() {
    LightJavaCodeInsightTestFixture myFixture = leakableMapKey.get().getFixture();
    List<IntentionAction> quickFixes = myFixture.getAllQuickFixes("Service2.java");
    assertEquals(2, quickFixes.size());

    myFixture.launchAction(quickFixes.get(1));
    myFixture.checkResultByFile("Service2.java", "Service2_withString.java", true);
  }
}
