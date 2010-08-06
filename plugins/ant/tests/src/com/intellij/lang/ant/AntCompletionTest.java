/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntCompletionTest extends LightCodeInsightTestCase {

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/completion/";
  }

  public void testSimpleProperty() throws Exception {
    configureByFile("test1.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot1() throws Exception {
    configureByFile("test2.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot2() throws Exception {
    configureByFile("test4.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot3() throws Exception {
    configureByFile("/test5.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testReplace1() throws Exception {
    configureByFile("test10.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile("test10-out.xml");
  }

  public void testSimplePropertyWithDot4() throws Exception {
    configureByFile("/test6.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDotAndMulityReference() throws Exception {
    configureByFile("test7.xml");
    performNormalCompletion();
    final LookupElement[] items = getItems();
    assertNotNull(items);
    assertTrue(items.length > 0);
    select();
  }

  public void testSimplePropertyWithNoPrefix() throws Exception {
    configureByFile("test3.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testInsertion1() throws Exception {
    configureByFile("test8.xml");
    performNormalCompletion();

    checkResultByFile("/test8-out.xml");
  }

  public void testInsertion2() throws Exception {
    configureByFile("test9.xml");
    performNormalCompletion();
    checkResultByFile("/test8-out.xml");
  }

  public void testTargetCompletion() throws Exception {
    configureByFile("targetCompletion.xml");
    performNormalCompletion();
    checkResultByFile("/targetCompletion-out.xml");
  }

  public void testTargetCompletion2() throws Exception {
    final String filePath = "targetCompletion2.xml";

    configureByFile(filePath);

    performNormalCompletion();
    select();
    checkResultByFile("/targetCompletion2-out.xml");
  }

  public void testEntityCompletion() throws Exception {
    configureByFile("EntityCompletion.xml");
    performNormalCompletion();

    checkResultByFile("EntityCompletion-out.xml");
  }

  public void testTargetAttributesCompletion() throws Exception {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    performNormalCompletion();

    final LookupElement[] lookupItems = getItems();
    assertNotNull("Target attributes should be present", lookupItems);
    assertTrue(lookupItems.length > 0);

    checkResultByFile(testName + "-out.xml");
  }

  public void testEndTagCompletion() throws Exception {
    doTest();
  }

  public void testTargetIfAttrEasterEgg() throws Exception {
    doTest();
  }

  public void testTargetUnlessAttrEasterEgg() throws Exception {
    doTest();
  }

  public void testMacrodefParam() throws Exception {
    doTest();
  }

  public void testMacrodefParam1() throws Exception {
    doTest();
  }

  public void testMacrodefNestedElement() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    performNormalCompletion();
    checkResultByFile(testName + "-out.xml");
  }

  private static void select() {
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
  }

  private static void performNormalCompletion() {
    new CodeCompletionHandlerBase(CompletionType.BASIC).invoke(getProject(), getEditor(), myFile);
  }

  private static void select(char completionChar, LookupElement item) {
    ((TestLookupManager)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private static LookupElement getSelected() {
    final Lookup lookup = LookupManager.getInstance(getProject()).getActiveLookup();
    return lookup.getCurrentItem();
  }

  @NotNull
  private static LookupElement[] getItems() {
    final List<LookupElement> list = LookupManager.getInstance(getProject()).getActiveLookup().getItems();
    return list.toArray(new LookupElement[list.size()]);
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

}
