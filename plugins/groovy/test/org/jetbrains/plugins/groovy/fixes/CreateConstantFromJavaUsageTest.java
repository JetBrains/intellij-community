// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;

public class CreateConstantFromJavaUsageTest extends GrHighlightingTestBase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createConstantFromJava/" + getTestName(true) + "/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_LATEST_REAL_JDK;
  }

  private void doTest(String action, int actionsCount) {
    final String beforeGroovy = "Before.groovy";
    final String afterGroovy = "After.groovy";
    final String javaClass = "Area.java";

    myFixture.configureByFiles(javaClass, beforeGroovy);

    myFixture.enableInspections(getCustomInspections());
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    assert fixes.size() == actionsCount;
    if (actionsCount == 0) return;

    myFixture.launchAction(fixes.get(0));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
  }

  private void doTest() {
    doTest("Create constant", 1);
  }

  public void testInterface() {
    doTest();
  }

  public void testConstantInitializer() {
    doTest();
  }

  public void testUppercaseInSuperInterface() {
    doTest("Create constant field 'BAR' in 'I'", 0);
  }
}
