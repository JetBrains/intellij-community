package com.intellij.appengine.highlighting;

import com.intellij.appengine.AppEngineCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;

/**
 * @author nik
 */
@TestDataPath("$CONTENT_ROOT/testData/highlighting/descriptor/")
public class AppEngineDescriptorHighlightingTest extends AppEngineCodeInsightTestCase {
  public void testNamespace() throws Exception {
    myCodeInsightFixture.configureByFile("appengine-web.xml");
    checkXmlHighlighting();
  }

  private void checkXmlHighlighting() throws Exception {
    myCodeInsightFixture.enableInspections(CheckXmlFileWithXercesValidatorInspection.class);
    myCodeInsightFixture.checkHighlighting();
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "highlighting/descriptor";
  }
}
