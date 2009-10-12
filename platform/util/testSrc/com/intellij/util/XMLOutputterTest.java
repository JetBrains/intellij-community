/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

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

  private String printElement(Element root) throws IOException {
    XMLOutputter xmlOutputter = JDOMUtil.createOutputter("\n");
    final Format format = xmlOutputter.getFormat().setOmitDeclaration(true).setOmitEncoding(true).setExpandEmptyElements(true);
    xmlOutputter.setFormat(format);

    CharArrayWriter writer = new CharArrayWriter();

    xmlOutputter.output(root, writer);
    String res = new String(writer.toCharArray());
    return res;
  }
}