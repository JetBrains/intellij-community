// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;

/**
 * @author Bas Leijdekkers
 */
public class CommunityDetectFileTypeTest extends DetectFileTypeTestCase {

  public void testDetectHtml() {
    doTest(HtmlFileType.INSTANCE, "<html><head><title>Hello <caret>Wrold</title></head></html>");
  }

  public void testDetectXml() {
    doTest(XmlFileType.INSTANCE, "<html><head><title>Hello <caret>Wrold</title></head></html>");
  }

  public void testDetectJava() {
    doTest(JavaFileType.INSTANCE, "class X {{  System.out.println<caret>();}}");
  }
}