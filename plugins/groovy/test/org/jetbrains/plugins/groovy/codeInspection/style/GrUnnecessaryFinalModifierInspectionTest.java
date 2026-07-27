// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

/**
 * @author Bas Leijdekkers
 */
public final class GrUnnecessaryFinalModifierInspectionTest extends LightGroovyTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_6_0;
  }

  public void testRecord() {
    doTest("<warning descr=\"Modifier 'final' is not necessary\">final</warning> record Point(int x, int y) {}",
           "record Point(int x, int y) {}");
  }

  public void testVal() {
    doTest("<warning descr=\"Modifier 'final' is not necessary\">final</warning> val x = 1",
           "val x = 1");
  }

  private void doTest(String before, String after) {
    myFixture.enableInspections(new GrUnnecessaryFinalModifierInspection());
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Remove unnecessary 'final'"));
    myFixture.checkResult(after);
  }
}
