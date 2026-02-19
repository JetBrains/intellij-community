// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions;

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.editorconfig.configmanagement.generate.EditorConfigGenerateLanguagePropertiesAction;

public class EditorConfigGenerateLanguagePropertiesTest extends CodeInsightFixtureTestCase {
  public void testGenerateProperties() {
    String name = getTestName(true);
    myFixture.configureByFile(name + "_before" + ".editorconfig");
    EditorConfigGenerateLanguagePropertiesAction.generateProperties(getProject(), myFixture.getEditor(), EditorConfigLanguage.INSTANCE);
    myFixture.checkResultByFile(name + "_after" + ".editorconfig");
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/generate/";
  }
}
