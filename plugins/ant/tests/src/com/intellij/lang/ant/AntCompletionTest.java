package com.intellij.lang.ant;

import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

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
    final LookupElement[] items = getItems();
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

  public void testTargetCompletion2() throws Exception {
    final String filePath = "targetCompletion2.xml";

    configureByFile(filePath);
    AntSupport.markFileAsAntFile(myVFile, myFile.getViewProvider(), true);

    performNormalCompletion();
    if (getItems() != null) {
      select();
    }
    checkResultByFile("/targetCompletion2-out.xml");
  }

  public void testEntityCompletion() throws Exception {
    configureByFile("EntityCompletion.xml");
    performNormalCompletion();
    if (getItems() != null) {
      select();
    }

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

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR, getSelected());
  }

  private void performNormalCompletion() {
    new CodeCompletionHandler().invoke(getProject(), getEditor(), AntSupport.getAntFile(myFile));
  }

  private void select(char completionChar, LookupElement item) {
    ((TestLookupManager)LookupManager.getInstance(getProject())).forceSelection(completionChar, item);
  }

  private LookupElement getSelected() {
    return LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem();
  }

  private LookupElement[] getItems() {
    return ((TestLookupManager)LookupManager.getInstance(getProject())).getItems();
  }

  protected void tearDown() throws Exception {
    if (getItems() != null) LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  //public static void main(String[] args) {
  //  new TestRunner().doRun(new TestSuite(AntCompletionTest.class));
  //}
}
