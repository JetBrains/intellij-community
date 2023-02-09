package com.intellij.htmltools.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitorBasedInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.xml.util.XmlUtil;

/**
 * @author Maxim.Mossienko
 */
public class GlobalHtmlHighlightingTest extends JavaInspectionTestCase {
  private String myOldDoctype;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testReportingXmlHighlightVisitorProblems() {
    doTest(getTestName(true), new XmlHighlightVisitorBasedInspection());
  }

  @Override
  public String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/inspections";
  }
}