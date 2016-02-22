/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import static com.intellij.openapi.editor.colors.FontPreferencesTest.*;
import static java.util.Collections.singletonList;

public class EditorColorsSchemeImplTest extends LightPlatformCodeInsightTestCase {
  EditorColorsSchemeImpl myScheme = new EditorColorsSchemeImpl(null);

  public void testDefaults() {
    checkState(myScheme.getFontPreferences(),
               Collections.<String>emptyList(),
               Collections.<String>emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getEditorFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               Collections.<String>emptyList(),
               Collections.<String>emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getConsoleFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getConsoleFontSize());
  }

  public void testSetPreferences() throws Exception {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.getFontPreferences().register(fontName1, 25);
    myScheme.getFontPreferences().register(fontName2, 13);
    myScheme.getConsoleFontPreferences().register(fontName1, 21);
    myScheme.getConsoleFontPreferences().register(fontName2, 15);

    checkState(myScheme.getFontPreferences(),
               Arrays.asList(fontName1, fontName2),
               Arrays.asList(fontName1, fontName2),
               fontName1,
               fontName1, 25,
               fontName2, 13);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               Arrays.asList(fontName1, fontName2),
               Arrays.asList(fontName1, fontName2),
               fontName1,
               fontName1, 21,
               fontName2, 15);
    assertEquals(fontName1, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  public void testSetName() throws Exception {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    myScheme.setConsoleFontName(fontName2);

    checkState(myScheme.getFontPreferences(),
               singletonList(fontName1),
               singletonList(fontName1),
               fontName1,
               fontName1, FontPreferences.DEFAULT_FONT_SIZE);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               singletonList(fontName2),
               singletonList(fontName2),
               fontName2,
               fontName2, FontPreferences.DEFAULT_FONT_SIZE);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getConsoleFontSize());
  }

  public void testSetSize() throws Exception {
    myScheme.setEditorFontSize(25);
    myScheme.setConsoleFontSize(21);

    checkState(myScheme.getFontPreferences(),
               singletonList(FontPreferences.DEFAULT_FONT_NAME),
               singletonList(FontPreferences.DEFAULT_FONT_NAME),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, 25);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               singletonList(FontPreferences.DEFAULT_FONT_NAME),
               singletonList(FontPreferences.DEFAULT_FONT_NAME),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, 21);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  public void testSetNameAndSize() throws Exception {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    myScheme.setEditorFontSize(25);
    myScheme.setConsoleFontName(fontName2);
    myScheme.setConsoleFontSize(21);

    checkState(myScheme.getFontPreferences(),
               singletonList(fontName1),
               singletonList(fontName1),
               fontName1,
               fontName1, 25);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               singletonList(fontName2),
               singletonList(fontName2),
               fontName2,
               fontName2, 21);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  public void testSetSizeAndName() throws Exception {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontSize(25);
    myScheme.setEditorFontName(fontName1);
    myScheme.setConsoleFontSize(21);
    myScheme.setConsoleFontName(fontName2);

    checkState(myScheme.getFontPreferences(),
               singletonList(fontName1),
               singletonList(fontName1),
               fontName1,
               fontName1, 25);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               singletonList(fontName2),
               singletonList(fontName2),
               fontName2,
               fontName2, 21);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  public void testWriteInheritedFromDefault() throws Exception {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("test");
    Element root = new Element("scheme");
    ((AbstractColorsScheme)editorColorsScheme).writeExternal(root);
    root.removeChildren("option"); // Remove font options
    assertXmlOutputEquals("<scheme name=\"test\" version=\"142\" parent_scheme=\"Default\" />", root);
  }

  public void testWriteInheritedFromDarcula() throws Exception {
    EditorColorsScheme darculaScheme = EditorColorsManager.getInstance().getScheme("Darcula");
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)darculaScheme.clone();
    editorColorsScheme.setName("test");
    Element root = new Element("scheme");
    ((AbstractColorsScheme)editorColorsScheme).writeExternal(root);
    root.removeChildren("option"); // Remove font options
    assertXmlOutputEquals("<scheme name=\"test\" version=\"142\" parent_scheme=\"Darcula\" />", root);
  }

  public void testSaveInheritance() throws Exception {
    Pair<EditorColorsScheme,TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.STATIC_METHOD, new TextAttributes());
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.STATIC_METHOD.getFallbackAttributeKey());
    assertSame(result.second, fallbackAttrs);
  }

  public void testSaveNoInheritanceAndDefaults() throws Exception {
    TextAttributes identifierAttrs = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME)
      .getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER);
    TextAttributes declarationAttrs = identifierAttrs.clone();
    Pair<EditorColorsScheme, TextAttributes> result =
      doTestWriteRead(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, declarationAttrs);
    TextAttributes fallbackAttrs = result.first.getAttributes(
      DefaultLanguageHighlighterColors.FUNCTION_DECLARATION.getFallbackAttributeKey()
    );
    assertEquals(result.second, fallbackAttrs);
    assertNotSame(result.second, fallbackAttrs);
  }

  public void testSaveInheritanceForEmptyAttrs() throws Exception {
    TextAttributes abstractMethodAttrs = new TextAttributes();
    assertTrue(abstractMethodAttrs.isFallbackEnabled());
    Pair<EditorColorsScheme, TextAttributes> result =
      doTestWriteRead(DefaultLanguageHighlighterColors.INSTANCE_FIELD, abstractMethodAttrs);
    TextAttributes fallbackAttrs = result.first.getAttributes(
      DefaultLanguageHighlighterColors.INSTANCE_FIELD.getFallbackAttributeKey()
    );
    TextAttributes directlyDefined =
      ((AbstractColorsScheme)result.first).getDirectlyDefinedAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    assertTrue(directlyDefined != null && directlyDefined.isFallbackEnabled());
    assertSame(fallbackAttrs, result.second);
  }


  public void testUpgradeFromVer141() throws Exception {
    TextAttributesKey constKey = DefaultLanguageHighlighterColors.CONSTANT;
    TextAttributesKey fallbackKey = constKey.getFallbackAttributeKey();
    assertNotNull(fallbackKey);

    EditorColorsScheme scheme = loadScheme(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<scheme name=\"Test\" version=\"141\" parent_scheme=\"Default\">\n" +
      "<attributes>" +
      "   <option name=\"TEXT\">\n" +
      "      <value>\n" +
      "           option name=\"FOREGROUND\" value=\"ffaaaa\" />\n" +
      "      </value>\n" +
      "   </option>" +
      "</attributes>" +
      "</scheme>\n"
    );

    TextAttributes constAttrs = scheme.getAttributes(constKey);
    TextAttributes fallbackAttrs = scheme.getAttributes(fallbackKey);
    assertNotSame(fallbackAttrs, constAttrs);
    assertEquals(Font.BOLD | Font.ITALIC, constAttrs.getFontType());

    TextAttributes classAttrs = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME);
    TextAttributes classFallbackAttrs = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME.getFallbackAttributeKey());
    assertSame(classFallbackAttrs, classAttrs);
  }


  private static EditorColorsScheme loadScheme(@NotNull String docText) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    InputSource inputSource = new InputSource(new StringReader(docText));
    org.w3c.dom.Document doc = docBuilder.parse(inputSource);
    Element root = new DOMBuilder().build(doc.getDocumentElement());

    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);

    targetScheme.readExternal(root);

    return targetScheme;
  }

  @NotNull
  public Pair<EditorColorsScheme,TextAttributes> doTestWriteRead(TextAttributesKey key, TextAttributes attributes)
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
    return Pair.create(targetScheme,targetAttrs);
  }

  private static void assertXmlOutputEquals(String expected, Element root) throws IOException {
    StringWriter writer = new StringWriter();
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(root, writer);
    String actual = writer.toString();
    assertEquals(expected, actual);
  }
}
