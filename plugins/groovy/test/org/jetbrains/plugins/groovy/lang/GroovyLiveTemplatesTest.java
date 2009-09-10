/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
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
    expandTemplate(myFixture.getEditor());
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public static void expandTemplate(final Editor editor) {
    new WriteCommandAction(editor.getProject()) {
      protected void run(Result result) throws Throwable {
        new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor);
        ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }.execute();
  }

}
