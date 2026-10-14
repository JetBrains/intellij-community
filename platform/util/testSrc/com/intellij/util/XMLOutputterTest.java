// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.jupiter.api.Test;

import java.io.CharArrayWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XMLOutputterTest {
  @Test
  public void testEscapesInElementBody() throws Exception {
    Element root = new Element("root");
    root.setText("\"<>&");

    assertEquals("<root>&quot;&lt;&gt;&amp;</root>", printElement(root));
  }

  @Test
  public void testEscapesInAttributeValue() throws Exception {
    Element root = new Element("root");
    root.setAttribute("escapes", "\"<>&\n\r\t");

    assertEquals("<root escapes=\"&quot;&lt;&gt;&amp;&#10;&#13;&#9;\"></root>", printElement(root));
  }

  private static String printElement(Element root) throws IOException {
    XMLOutputter xmlOutputter = JDOMUtil.createOutputter("\n");
    Format format = xmlOutputter.getFormat().setOmitDeclaration(true).setOmitEncoding(true).setExpandEmptyElements(true);
    xmlOutputter.setFormat(format);

    CharArrayWriter writer = new CharArrayWriter();

    xmlOutputter.output(root, writer);
    return new String(writer.toCharArray());
  }
}