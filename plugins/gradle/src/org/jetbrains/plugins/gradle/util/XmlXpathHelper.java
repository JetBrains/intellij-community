/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * {@link XmlXpathHelper} utility class for query of XML using XPath expressions.
 *
 * @author Vladislav.Soroka
 * @since 2/20/14
 */
public class XmlXpathHelper {
  private final XPath xpath;
  private Document xmlDocument;

  /**
   * Parses the given XML string as a DOM document, using the JDK parser. The parser does not
   * validate, and is namespace aware.
   *
   * @param xml            the XML content to be parsed (must be well formed)
   * @param namespaceAware whether the parser is namespace aware
   * @throws XmlParserException
   */
  public XmlXpathHelper(String xml, boolean namespaceAware) throws XmlParserException {
    xpath = XPathFactory.newInstance().newXPath();
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      InputSource is = new InputSource(new StringReader(xml));
      factory.setNamespaceAware(namespaceAware);
      factory.setValidating(false);
      xmlDocument = factory.newDocumentBuilder().parse(is);
    }
    catch (Exception ex) {
      throw new XmlParserException(ex);
    }
  }

  public XmlXpathHelper(String xml) throws XmlParserException {
    this(xml, false);
  }

  public String queryXml(final String xpathExpr) throws XmlParserException {
    try {
      return xmlDocument == null ? "" : xpath.evaluate(xpathExpr, xmlDocument);
    }
    catch (XPathExpressionException ex) {
      throw new XmlParserException(ex);
    }
  }

  /**
   * Extracts the String value for the given expression.
   *
   * @param node
   * @param expr
   * @return
   */
  public String getValue(Node node, String expr) throws XmlParserException {
    try {
      return xpath.compile(expr).evaluate(node);
    }
    catch (XPathExpressionException ex) {
      throw new XmlParserException(ex);
    }
  }

  /**
   * {@link XmlParserException} indicates errors during xml processing.
   */
  public static class XmlParserException extends Exception {
    public XmlParserException(final Throwable cause) {
      super(cause);
    }

    public XmlParserException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public XmlParserException(final String message) {
      super(message);
    }
  }
}
