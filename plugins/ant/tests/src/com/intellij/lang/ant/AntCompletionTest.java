package com.intellij.lang.ant;

import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import java.util.Arrays;

/**
 * @author zhzhot
 */
public class AntCompletionTest extends LightCodeInsightTestCase {

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data/psi/completion/";
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
    final LookupItem[] items = getItems();
    assertNotNull(items);
    assertTrue(items.length > 0);
    Arrays.sort(items);
    //assertTrue(Arrays.binarySearch(items, "java.lang") > 0);
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
    if (getItems() != null) {
      select();
    }

    checkResultByFile("/test8-out.xml");
  }

  public void testInsertion2() throws Exception {
    configureByFile("test9.xml");
    performNormalCompletion();
    if (getItems() != null) {
      select();
    }
    checkResultByFile("/test8-out.xml");
  }

  public void testTargetCompletion() throws Exception {
    configureByFile("targetCompletion.xml");
    performNormalCompletion();
    if (getItems() != null) {
      select();
    }
    checkResultByFile("/targetCompletion-out.xml");
  }

  /*public void testEntityCompletion() throws Exception {
    configureByFile("EntityCompletion.xml");
    performNormalCompletion();
    if (getItems() != null) {
      select();
    }

    checkResultByFile("EntityCompletion-out.xml");
  }*/

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
  }

  private void performNormalCompletion() {
    CodeCompletionHandler handler = new CodeCompletionHandler() {
      protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
      }
    };
    handler.invoke(getProject(), getEditor(), getFile());
  }

  private void select(char completionChar, LookupItem item) {
    ((TestLookupManager)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private LookupItem getSelected() {
    return LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem();
  }

  private LookupItem[] getItems() {
    return ((TestLookupManager)LookupManager.getInstance(getProject())).getItems();
  }

  protected void tearDown() throws Exception {
    if (getItems() != null) LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  public static void main(String[] args) {
    new TestRunner().doRun(new TestSuite(AntCompletionTest.class));
  }
}
