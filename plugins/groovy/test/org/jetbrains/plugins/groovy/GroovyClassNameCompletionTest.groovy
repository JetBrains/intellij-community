/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameCompletionTest extends LightCodeInsightFixtureTestCase {
  private boolean old;

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/classNameCompletion";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = old;
    super.tearDown();
  }

  public void doTest(boolean force) throws Exception {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.CLASS_NAME);
    if (force) forceCompletion();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void forceCompletion() {
    CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(CompletionType.CLASS_NAME);
    handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    final LookupManager instance = LookupManager.getInstance(myFixture.getProject());
    if(instance instanceof TestLookupManager){
      final TestLookupManager testLookupManager = ((TestLookupManager)instance);
      if(testLookupManager.getActiveLookup() != null)
        testLookupManager.forceSelection(Lookup.NORMAL_SELECT_CHAR, 1);
    }
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) throws IOException {
    StringBuilder builder = new StringBuilder();
    if (packageName != null) builder.append("package ").append(packageName).append(";");
    builder.append("class ").append(name).append("{}");
    myFixture.addClass(builder.toString());
  }

  public void testInFieldDeclaration() throws Exception {doTest(false);}
  public void testInParameter() throws Exception {doTest(false);}
  public void testInImport() throws Exception {doTest(false);}
  public void testWhenClassExistsInSamePackage() throws Exception {doTest(true);}
  public void testInComment() throws Exception {doTest(false);}
  public void testInTypeElementPlace() throws Exception {doTest(false);}
  public void testWhenImportExists() throws Exception{doTest(false);}

  public void testFinishByDot() throws Exception{
    addClassToProject("a", "FooBar");
    myFixture.configureByText("a.groovy", "FB<caret>a")
    myFixture.complete(CompletionType.CLASS_NAME)
    myFixture.type '.'.charAt(0)
    myFixture.checkResult "a.FooBar.<caret>a"
  }
  
  public void testDelegateBasicToClassName() throws Exception{
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>a")
    myFixture.completeBasic()
    myFixture.type '.'.charAt(0)
    myFixture.checkResult "a.FooBarGooDoo.<caret>a"
  }

}
