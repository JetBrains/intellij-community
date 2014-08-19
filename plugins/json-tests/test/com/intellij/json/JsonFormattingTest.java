package com.intellij.json;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingTest extends FormatterTestCase {
  private static final Logger LOG = Logger.getInstance(JsonFormattingTest.class);

  @Override
  protected void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }

  @Override
  protected String getBasePath() {
    return "formatting";
  }

  // Why the heck do I specify path to testData in each kind of test differently?
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/plugins/json-tests/testData/";
  }

  @Override
  protected String getFileExtension() {
    return "json";
  }

  public void testContainerElementsAlignment() throws Exception {
    doTest();
  }

  public void testBlankLinesStripping() throws Exception {
    doTest();
  }

  public void testSpacesInsertion() throws Exception {
    doTest();
  }

  public void testWrapping() throws Exception {
    CodeStyleSettings settings = getSettings();
    int indentSize = settings.getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions().INDENT_SIZE;
//    LOG.debug("Intend size for JSON: " + indentSize);
    settings.RIGHT_MARGIN = 20;
    doTest();
  }

  @Override
  protected String getTestName(boolean ignored) {
    // always use uppercase first letter for consistency
    return super.getTestName(false);
  }
}
