// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.JavaXmlDocumentKt;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * {@link TestEventXmlXPathView} utility class for query of XML using XPath expressions.
 *
 * @author Vladislav.Soroka
 */
public class TestEventXmlXPathView implements TestEventXmlView {
  private final XPath xpath;
  private final Document xmlDocument;

  /**
   * Parses the given XML string as a DOM document, using the JDK parser. The parser does not
   * validate, and is namespace aware.
   *
   * @param xml            the XML content to be parsed (must be well formed)
   */
  public TestEventXmlXPathView(String xml) throws XmlParserException {
    xpath = XPathFactory.newDefaultInstance().newXPath();
    try {
      InputSource is = new InputSource(new StringReader(xml));
      xmlDocument = JavaXmlDocumentKt.createDocumentBuilder().parse(is);
    }
    catch (Exception ex) {
      throw new XmlParserException(ex);
    }
  }

  @Override
  public @NotNull String getTestEventType() throws TestEventXmlXPathView.XmlParserException {
    return queryXml("/ijLog/event/@type");
  }

  @Override
  public @NotNull String getTestName() throws XmlParserException {
    return queryXml("/ijLog/event/test/descriptor/@name");
  }

  @Override
  public @NotNull String getTestDisplayName() throws XmlParserException {
    String displayName = queryXml("/ijLog/event/test/descriptor/@displayName");
    return Strings.isEmpty(displayName)? getTestName(): displayName;
  }

  @Override
  public @NotNull String getTestParentId() throws XmlParserException {
    return queryXml("/ijLog/event/test/@parentId");
  }

  @Override
  public @NotNull String getTestId() throws XmlParserException {
    return queryXml("/ijLog/event/test/@id");
  }

  @Override
  public @NotNull String getTestClassName() throws XmlParserException {
    return queryXml("/ijLog/event/test/descriptor/@className");
  }

  @Override
  public @NotNull String getTestEventResultType() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@resultType");
  }

  @Override
  public @NotNull String getEventTitle() throws XmlParserException {
    return queryXml("/ijLog/event/title");
  }

  @Override
  public boolean isEventOpenSettings() throws XmlParserException {
    return Boolean.parseBoolean(queryXml("/ijLog/event/@openSettings"));
  }

  @Override
  public @NotNull String getEventMessage() throws XmlParserException {
    return queryXml("/ijLog/event/message");
  }

  @Override
  public @NotNull String getTestEventTest() throws XmlParserException {
    return queryXml("/ijLog/event/test/event");
  }

  @Override
  public @NotNull String getTestEventTestDescription() throws XmlParserException {
    return queryXml("/ijLog/event/test/event/@destination");
  }

  @Override
  public @NotNull String getEventTestReport() throws XmlParserException {
    return queryXml("/ijLog/event/@testReport");
  }

  @Override
  public @NotNull String getEventTestResultActualFilePath() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/actualFilePath");
  }

  @Override
  public @NotNull String getEventTestResultFilePath() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/filePath");
  }

  @Override
  public @NotNull String getEventTestResultExpected() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/expected");
  }

  @Override
  public @NotNull String getEventTestResultActual() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/actual");
  }

  @Override
  public @NotNull String getEventTestResultFailureType() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/failureType");
  }

  @Override
  public @NotNull String getEventTestResultExceptionName() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/exceptionName");
  }

  @Override
  public @NotNull String getEventTestResultStackTrace() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/stackTrace");
  }

  @Override
  public @NotNull String getEventTestResultErrorMsg() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/errorMsg");
  }

  @Override
  public @NotNull String getEventTestResultEndTime() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@endTime");
  }

  @Override
  public @NotNull String getEventTestResultStartTime() throws XmlParserException {
    return queryXml("/ijLog/event/test/result/@startTime");
  }

  private @NotNull String queryXml(final String xpathExpr) throws XmlParserException {
    try {
      return xmlDocument == null ? "" : xpath.evaluate(xpathExpr, xmlDocument);
    }
    catch (XPathExpressionException ex) {
      throw new XmlParserException(ex);
    }
  }
}
