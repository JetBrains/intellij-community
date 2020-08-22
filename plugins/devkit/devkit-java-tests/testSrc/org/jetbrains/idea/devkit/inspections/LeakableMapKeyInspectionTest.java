// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.Assert;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/inspections/leakableMapKey")
public class LeakableMapKeyInspectionTest extends LeakableMapKeyInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/leakableMapKey";
  }

  @Override
  public void testHighlighting() {
    myFixture.testHighlighting("Service.java");
  }

  public void testReplaceWithSuperString() {
    List<IntentionAction> quickFixes = myFixture.getAllQuickFixes("Service2.java");
    Assert.assertEquals(2, quickFixes.size());

    myFixture.launchAction(quickFixes.get(0));
    myFixture.checkResultByFile("Service2.java", "Service2_withSuperString.java", true);
  }

  public void testReplaceWithString() {
    List<IntentionAction> quickFixes = myFixture.getAllQuickFixes("Service2.java");
    Assert.assertEquals(2, quickFixes.size());

    myFixture.launchAction(quickFixes.get(1));
    myFixture.checkResultByFile("Service2.java", "Service2_withString.java", true);
  }
}
