/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/SafeXml.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class SafeXml {

  public static boolean isLegalCharacter(int codePoint) {
    return codePoint == 0x9
           || codePoint == 0xA
           || codePoint == 0xD
           || (codePoint >= 0x20 && codePoint <= 0xD7FF)
           || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
           || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
  }

  public static String escapeIllegalCharacters(String text) {
    if (text == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();
    text.codePoints()
      .forEach(
        codePoint -> {
          if (isLegalCharacter(codePoint)) {
            result.appendCodePoint(codePoint);
          } else {
            result.append("&#").append(codePoint).append(';');
          }
        });
    return result.toString();
  }

  public static void writeTextElement(XMLStreamWriter xml, String elementName, String text)
    throws XMLStreamException {
    if (text == null) {
      return;
    }

    xml.writeStartElement(elementName);

    writeCData(xml, text);
    xml.writeEndElement();
  }

  public static void writeCData(XMLStreamWriter xml, String text) throws XMLStreamException {
    if (text == null) {
      return;
    }
    // If the text content contains a cdata end tag, then the generated xml won't
    // be parseable. Replace the end tag with a new cdata section, so everything
    // works as expected
    xml.writeCData(escapeIllegalCharacters(text.replace("]]>", "]]]]><![CDATA[>")));
  }
}