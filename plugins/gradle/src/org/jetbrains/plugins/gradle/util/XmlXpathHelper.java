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

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
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
   * @throws XmlParserException
   */
  public XmlXpathHelper(String xml) throws XmlParserException {
    xpath = XPathFactory.newInstance().newXPath();
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      InputSource is = new InputSource(new StringReader(xml));
      factory.setNamespaceAware(false);
      factory.setValidating(false);
      xmlDocument = factory.newDocumentBuilder().parse(is);
    }
    catch (Exception ex) {
      throw new XmlParserException(ex);
    };
  }

  @NotNull
  public String getTestEventType() throws XmlXpathHelper.XmlParserException {
    return queryXml("/ijLog/event/@type");
  }

  public String getTestName() throws XmlParserException {
    return queryXml("/ijLog/event/test/descriptor/@name");
  }

  public String getParentTestId() throws XmlParserException {
    return queryXml("/ijLog/event/test/@parentId");
  }

  public String getTestId() throws XmlParserException {
    return queryXml("/ijLog/event/test/@id");
  }

  public String getTestClassName() throws XmlParserException {
    return queryXml("/ijLog/event/test/descriptor/@className");
  }

  @NotNull
  public String getTestEventResultType() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@resultType");
  }

  public String getEventTitle() throws XmlParserException {
    return queryXml("/ijLog/event/title");
  }

  public String isEventOpenSettings() throws XmlParserException {
    return queryXml("/ijLog/event/@openSettings");
  }

  public String getEventMessage() throws XmlParserException {
    return queryXml("/ijLog/event/message");
  }

  public String getTestEventTest() throws XmlParserException {
    return queryXml("/ijLog/event/test/event");
  }

  public String getTestEventTestDescription() throws XmlParserException {
    return queryXml("/ijLog/event/test/event/@destination");
  }

  public String getEventTestReport() throws XmlParserException {
    return queryXml("/ijLog/event/@testReport");
  }

  public String getEventTestResultActualFilePath() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/actualFilePath");
  }

  public String getEventTestResultFilePath() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/filePath");
  }

  public String getEventTestResultExpected() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/expected");
  }

  public String getEventTestResultActual() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/actual");
  }

  public String getEventTestResultFailureType() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/failureType");
  }

  public String getEventTestResultStackTrace() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/stackTrace");
  }

  public String getEventTestResultErrorMsg() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/errorMsg");
  }

  public String getEventTestResultEndTime() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@endTime");
  }

  public String getEventTestResultStartTime() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@startTime");
  }

  private String queryXml(final String xpathExpr) throws XmlParserException {
    try {
      return xmlDocument == null ? "" : xpath.evaluate(xpathExpr, xmlDocument);
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
