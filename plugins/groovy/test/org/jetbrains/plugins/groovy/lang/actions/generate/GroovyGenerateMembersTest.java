/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.actions.generate;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.actions.generate.constructors.ConstructorGenerateHandler;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author peter
 */
public class GroovyGenerateMembersTest extends LightCodeInsightFixtureTestCase {

  public void testConstructorAtOffset() throws Throwable {
    doTest();
  }

  public void testConstructorAtEnd() throws Throwable {
    doTest();
  }
  
  public void testLonelyConstructor() throws Throwable {
    doTest();
  }

  private void doTest() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        new ConstructorGenerateHandler().invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "generate";
  }
}
