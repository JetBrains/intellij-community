// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author Bas Leijdekkers
 */
public class CommunityDetectFileTypeTest extends DetectFileTypeTestCase {

  public void testDetectHtml() {
    doTest(StdFileTypes.HTML, "<html><head><title>Hello <caret>Wrold</title></head></html>");
  }

  public void testDetectXml() {
    doTest(StdFileTypes.XML, "<html><head><title>Hello <caret>Wrold</title></head></html>");
  }

  public void testDetectJava() {
    doTest(StdFileTypes.JAVA, "class X {{  System.out.println<caret>();}}");
  }
}