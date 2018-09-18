// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.CharArrayWriter;
import java.io.IOException;

public class XMLOutputterTest extends TestCase {
  public void testEscapesInElementBody() throws Exception {
    Element root = new Element("root");
    root.setText("\"<>&");

    assertEquals("<root>&quot;&lt;&gt;&amp;</root>", printElement(root));
  }

  public void testEscapesInAttributeValue() throws Exception {
    Element root = new Element("root");
    root.setAttribute("escapes", "\"<>&\n\r\t");

    assertEquals("<root escapes=\"&quot;&lt;&gt;&amp;&#10;&#13;&#9;\"></root>", printElement(root));
  }

  private static String printElement(Element root) throws IOException {
    XMLOutputter xmlOutputter = JDOMUtil.createOutputter("\n");
    final Format format = xmlOutputter.getFormat().setOmitDeclaration(true).setOmitEncoding(true).setExpandEmptyElements(true);
    xmlOutputter.setFormat(format);

    CharArrayWriter writer = new CharArrayWriter();

    xmlOutputter.output(root, writer);
    return new String(writer.toCharArray());
  }
}