// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.highlighter.HtmlFileType;
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
}