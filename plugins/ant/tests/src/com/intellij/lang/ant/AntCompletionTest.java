// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntCompletionTest extends LightCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/completion/";
  }

  @Override
  protected void configureByFile(@NotNull @TestDataFile @NonNls String filePath) {
    super.configureByFile(filePath);
    AntSupport.markFileAsAntFile(myVFile, myFile.getProject(), true);
  }

  public void testSimpleProperty() {
    configureByFile("test1.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot1() {
    configureByFile("test2.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot2() {
    configureByFile("test4.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDot3() {
    configureByFile("/test5.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testReplace1() {
    configureByFile("test10.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    select(Lookup.REPLACE_SELECT_CHAR, getSelected());
    checkResultByFile("test10-out.xml");
  }

  public void testSimplePropertyWithDot4() {
    configureByFile("/test6.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testSimplePropertyWithDotAndMulityReference() {
    configureByFile("test7.xml");
    performNormalCompletion();
    final LookupElement[] items = getItems();
    assertNotNull(items);
    assertTrue(items.length > 0);
    select();
  }

  public void testSimplePropertyWithNoPrefix() {
    configureByFile("test3.xml");
    performNormalCompletion();
    assertNotNull(getItems());
    assertTrue(getItems().length > 0);
    select();
  }

  public void testInsertion1() {
    configureByFile("test8.xml");
    performNormalCompletion();

    checkResultByFile("/test8-out.xml");
  }

  public void testInsertion2() {
    configureByFile("test9.xml");
    performNormalCompletion();
    checkResultByFile("/test8-out.xml");
  }

  public void testTargetCompletion() {
    configureByFile("targetCompletion.xml");
    performNormalCompletion();
    checkResultByFile("/targetCompletion-out.xml");
  }

  public void testTargetCompletion2() {
    final String filePath = "targetCompletion2.xml";

    configureByFile(filePath);

    performNormalCompletion();
    //select();
    checkResultByFile("/targetCompletion2-out.xml");
  }

  public void testEntityCompletion() {
    configureByFile("EntityCompletion.xml");
    performNormalCompletion();

    checkResultByFile("EntityCompletion-out.xml");
  }

  public void testTargetAttributesCompletion() {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    performNormalCompletion();

    final LookupElement[] lookupItems = getItems();
    assertNotNull("Target attributes should be present", lookupItems);
    assertTrue(lookupItems.length > 0);

    checkResultByFile(testName + "-out.xml");
  }

  public void testEndTagCompletion() {
    doTest();
  }

  public void testTargetIfAttrEasterEgg() {
    doTest();
  }

  public void testTargetUnlessAttrEasterEgg() {
    doTest();
  }

  // TODO implement refs to params
  public void _testMacrodefParam() {
    doTest();
  }

  // TODO implement refs to params
  public void _testMacrodefParam1() {
    doTest();
  }

  public void testMacrodefNestedElement() {
    doTest();
  }

  private void doTest() {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    performNormalCompletion();
    checkResultByFile(testName + "-out.xml");
  }

  private static void select() {
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
  }

  private static void performNormalCompletion() {
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), getEditor());
  }

  private static void select(char completionChar, LookupElement item) {
    ((LookupManagerImpl)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private static LookupElement getSelected() {
    final Lookup lookup = LookupManager.getInstance(getProject()).getActiveLookup();
    return lookup.getCurrentItem();
  }

  @NotNull
  private static LookupElement[] getItems() {
    final List<LookupElement> list = LookupManager.getInstance(getProject()).getActiveLookup().getItems();
    return list.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LookupManager.hideActiveLookup(getProject());
    }
    finally {
      super.tearDown();
    }
  }

}
