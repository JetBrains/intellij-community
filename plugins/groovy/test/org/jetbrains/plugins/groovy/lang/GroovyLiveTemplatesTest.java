/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class GroovyLiveTemplatesTest extends LightCodeInsightFixtureTestCase{
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/liveTemplates/";
  }

  public void testJavaTemplatesWorkInGroovyContext() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    expandTemplate();
    myFixture.checkResultByFile(getTestName(false) + ".groovy");
  }

  private void expandTemplate() {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        new ListTemplatesAction().actionPerformedImpl(getProject(), myFixture.getEditor());
      }
    }.execute();
  }

  public void testHtmlTemplatesWorkInGsp() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".gsp");
    expandTemplate();
    myFixture.checkResultByFile(getTestName(false) + ".gsp");
  }

}
