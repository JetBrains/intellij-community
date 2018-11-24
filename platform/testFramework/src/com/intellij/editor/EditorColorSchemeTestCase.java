/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.editor;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public abstract class EditorColorSchemeTestCase extends LightPlatformTestCase {
  protected static EditorColorsScheme loadScheme(@NotNull String docText) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    InputSource inputSource = new InputSource(new StringReader(docText));
    Document doc = docBuilder.parse(inputSource);
    Element root = new DOMBuilder().build(doc.getDocumentElement());

    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);

    targetScheme.readExternal(root);

    return targetScheme;
  }

  @NotNull
  protected Pair<EditorColorsScheme, TextAttributes> doTestWriteRead(TextAttributesKey key, TextAttributes attributes)
    throws WriteExternalException {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);

    EditorColorsScheme sourceScheme = (EditorColorsScheme)defaultScheme.clone();
    sourceScheme.setName("test");
    sourceScheme.setAttributes(key, attributes);

    Element root = new Element("scheme");
    ((AbstractColorsScheme)sourceScheme).writeExternal(root);

    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);
    targetScheme.readExternal(root);
    assertEquals("test", targetScheme.getName());
    TextAttributes targetAttrs = targetScheme.getAttributes(key);
    return Pair.create(targetScheme, targetAttrs);
  }

  protected static void assertXmlOutputEquals(String expected, Element root) throws IOException {
    StringWriter writer = new StringWriter();
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(root, writer);
    String actual = writer.toString();
    assertEquals(expected, actual);
  }
}
