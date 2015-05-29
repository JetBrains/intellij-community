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

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
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
    assertXmlOutputEquals("<scheme name=\"test\" version=\"141\" parent_scheme=\"Default\" />", root);
  }

  public void testWriteInheritedFromDarcula() throws Exception {
    EditorColorsScheme darculaScheme = EditorColorsManager.getInstance().getScheme("Darcula");
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)darculaScheme.clone();
    editorColorsScheme.setName("test");
    Element root = new Element("scheme");
    ((AbstractColorsScheme)editorColorsScheme).writeExternal(root);
    root.removeChildren("option"); // Remove font options
    assertXmlOutputEquals("<scheme name=\"test\" version=\"141\" parent_scheme=\"Darcula\" />", root);
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
