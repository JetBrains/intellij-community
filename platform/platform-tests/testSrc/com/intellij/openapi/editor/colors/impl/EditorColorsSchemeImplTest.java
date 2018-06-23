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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.editor.EditorColorSchemeTestCase;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;

import static com.intellij.openapi.editor.colors.FontPreferencesTest.*;
import static com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.INHERITED_ATTRS_MARKER;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static java.util.Collections.singletonList;

@SuppressWarnings("Duplicates")
public class EditorColorsSchemeImplTest extends EditorColorSchemeTestCase {
  private EditorColorsSchemeImpl myScheme = new EditorColorsSchemeImpl(null);

  @Override
  protected void tearDown() throws Exception {
    myScheme = null;
    super.tearDown();
  }

  public void testAppLevelEditorFontDefaults() {
    ModifiableFontPreferences appFontPrefs = (ModifiableFontPreferences)AppEditorFontOptions.getInstance().getFontPreferences();
    FontPreferences stored = new FontPreferencesImpl();
    appFontPrefs.copyTo(stored);
    try {
      String appFontName = appFontPrefs.getFontFamily();
      int appFontSize = appFontPrefs.getSize(appFontName);
      assertEquals(FontPreferences.DEFAULT_FONT_NAME, appFontName);
      assertEditorFontsEqual(appFontName, appFontSize);
      appFontPrefs.setFontSize(FontPreferences.DEFAULT_FONT_NAME, 8);
      assertEditorFontsEqual(appFontName, 8);
    }
    finally {
      stored.copyTo(appFontPrefs);
    }
  }

  /**
   * TODO<rv> FIX PROPERLY
   * This is a hack: since font name is taken from default scheme (why?) where it is explicitly defined as "Dejavu Sans", font names
   * do not match because default font name on linux in headless environment falls back to FALLBACK_FONT_FAMILY
   */
  private static String substLinuxFontName(@NotNull String fontName) {
    return SystemInfo.isLinux && GraphicsEnvironment.isHeadless() && FontPreferences.LINUX_DEFAULT_FONT_FAMILY.equals(fontName)?
           FontPreferences.FALLBACK_FONT_FAMILY :
           fontName;
  }

  private void assertEditorFontsEqual(@NotNull String fontName, int fontSize) {
    assertEquals(fontName, substLinuxFontName(myScheme.getEditorFontName()));
    assertEquals(fontSize, myScheme.getEditorFontSize());
    assertEquals(fontName, substLinuxFontName(myScheme.getConsoleFontName()));
    assertEquals(fontSize, myScheme.getConsoleFontSize());
  }

  public void testDefaults() {
    myScheme.setFontPreferences(new FontPreferencesImpl());
    checkState(myScheme.getFontPreferences(),
               Collections.emptyList(),
               Collections.emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
    String expectedName = FontPreferences.DEFAULT_FONT_NAME;
    assertEquals(expectedName, myScheme.getEditorFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               Collections.emptyList(),
               Collections.emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getConsoleFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getConsoleFontSize());
  }

  public void testSetFontPreferences() {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    FontPreferences fontPreferences = myScheme.getFontPreferences();
    assertInstanceOf(fontPreferences, ModifiableFontPreferences.class);
    ((ModifiableFontPreferences)fontPreferences).register(fontName1, 25);
    ((ModifiableFontPreferences)fontPreferences).register(fontName2, 13);
    FontPreferences consoleFontPreferences = myScheme.getConsoleFontPreferences();
    assertInstanceOf(consoleFontPreferences, FontPreferences.class);
    myScheme.setConsoleFontSize(10);
    consoleFontPreferences = myScheme.getConsoleFontPreferences();
    assertInstanceOf(consoleFontPreferences, ModifiableFontPreferences.class);
    ((ModifiableFontPreferences)consoleFontPreferences).register(fontName1, 21);
    ((ModifiableFontPreferences)consoleFontPreferences).register(fontName2, 15);

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

    myScheme.setUseEditorFontPreferencesInConsole();
    checkState(myScheme.getConsoleFontPreferences(),
               Arrays.asList(fontName1, fontName2),
               Arrays.asList(fontName1, fontName2),
               fontName1,
               fontName1, 25,
               fontName2, 13);

  }

  public void testSetName() {
    String fontName1 = getExistingNonDefaultFontName();
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    myScheme.setConsoleFontName(fontName2);
    int scaledSize = UISettings.restoreFontSize(FontPreferences.DEFAULT_FONT_SIZE, 1.0f);

    checkState(myScheme.getFontPreferences(),
               singletonList(fontName1),
               singletonList(fontName1),
               fontName1,
               fontName1, scaledSize);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(scaledSize, myScheme.getEditorFontSize());
    checkState(myScheme.getConsoleFontPreferences(),
               singletonList(fontName2),
               singletonList(fontName2),
               fontName2,
               fontName2, scaledSize);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(scaledSize, myScheme.getConsoleFontSize());
  }

  public void testSetSize() {
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

  public void testSetNameAndSize() {
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

  public void testSetSizeAndName() {
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
    assertXmlOutputEquals(
      "<scheme name=\"test\" version=\"142\" parent_scheme=\"Default\" />",
      serialize(editorColorsScheme));

    String fontName = editorColorsScheme.getEditorFontName();

    editorColorsScheme.setConsoleFontName(fontName);
    editorColorsScheme.setConsoleFontSize(10);
    assertXmlOutputEquals(
      "<scheme name=\"test\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <option name=\"CONSOLE_FONT_NAME\" value=\"Test\" />\n" +
      "  <option name=\"CONSOLE_FONT_SIZE\" value=\"10\" />\n" +
      "  <option name=\"CONSOLE_LINE_SPACING\" value=\"1.0\" />\n" +
      "</scheme>",
      serialize(editorColorsScheme));
  }

  public void testWriteInheritedFromDarcula() throws Exception {
    EditorColorsScheme darculaScheme = EditorColorsManager.getInstance().getScheme("Darcula");
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)darculaScheme.clone();
    editorColorsScheme.setName("test");
    assertXmlOutputEquals(
      "<scheme name=\"test\" version=\"142\" parent_scheme=\"Darcula\" />",
      serialize(editorColorsScheme));
  }


  public void testSaveInheritance() {
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.STATIC_METHOD, INHERITED_ATTRS_MARKER);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.STATIC_METHOD.getFallbackAttributeKey());
    assertSame(result.second, fallbackAttrs);
  }

  public void testSaveNoInheritanceAndDefaults() {
    TextAttributes declarationAttrs = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME)
      .getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER).clone();
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, declarationAttrs);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION.getFallbackAttributeKey());
    assertThat(result.second).isEqualTo(fallbackAttrs);
  }

  public void testSaveInheritanceForEmptyAttrs() {
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.INSTANCE_FIELD, INHERITED_ATTRS_MARKER);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD.getFallbackAttributeKey());
    TextAttributes directlyDefined =
      ((AbstractColorsScheme)result.first).getDirectlyDefinedAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    assertTrue(directlyDefined != null && directlyDefined == INHERITED_ATTRS_MARKER);
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

  public void testPreventCyclicTextAttributeDependency() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("test");
    TextAttributesKey keyD = TextAttributesKey.createTextAttributesKey("D");
    TextAttributesKey keyC = TextAttributesKey.createTextAttributesKey("C", keyD);
    TextAttributesKey keyB = TextAttributesKey.createTextAttributesKey("B", keyC);
    TextAttributesKey keyA = TextAttributesKey.createTextAttributesKey("A", keyB);
    try {
      keyD.setFallbackAttributeKey(keyB);
      editorColorsScheme.getAttributes(keyA);
    }
    catch (StackOverflowError e) {
      fail("Stack overflow detected!");
    }
    catch (Throwable e) {
      String s = e.getMessage();
      assertTrue(s.contains("B->C->D"));
    }
    finally {
      TextAttributesKey.removeTextAttributesKey("A");
      TextAttributesKey.removeTextAttributesKey("B");
      TextAttributesKey.removeTextAttributesKey("C");
      TextAttributesKey.removeTextAttributesKey("D");
    }
  }

  public void testIdea152156() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme parentScheme = (EditorColorsScheme)defaultScheme.clone();
    parentScheme.setName("DefaultTest");
    AbstractColorsScheme editorColorsScheme = new EditorColorsSchemeImpl(parentScheme);
    editorColorsScheme.setName("test");
    TextAttributes defaultAttributes = new TextAttributes(null, null, Color.BLACK, EffectType.LINE_UNDERSCORE, Font.PLAIN);
    TextAttributesKey testKey = TextAttributesKey.createTextAttributesKey("TEST_KEY", DefaultLanguageHighlighterColors.PARAMETER);
    parentScheme.setAttributes(testKey, defaultAttributes);
    editorColorsScheme.setAttributes(testKey, INHERITED_ATTRS_MARKER);
    try {
      Element root = new Element("scheme");
      editorColorsScheme.writeExternal(root);
      AbstractColorsScheme targetScheme = new EditorColorsSchemeImpl(parentScheme);
      for (final Element child : root.getChildren()) {
        if ("attributes".equals(child.getName())) {
          targetScheme.readAttributes(child);
        }
      }
      TextAttributes targetAttributes = targetScheme.getDirectlyDefinedAttributes(testKey);
      assertTrue(targetAttributes != null && targetAttributes == INHERITED_ATTRS_MARKER);
    }
    finally {
      TextAttributesKey.removeTextAttributesKey(testKey.getExternalName());
    }
  }

  public void testWriteDefaultSemanticHighlighting() throws Exception {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("rainbow");

    final String BEGIN =
      "<scheme name=\"rainbow\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <metaInfo>\n" +
      "    <property name=\"created\" />\n" +
      "    <property name=\"ide\" />\n" +
      "    <property name=\"ideVersion\" />\n" +
      "    <property name=\"modified\" />\n" +
      "    <property name=\"originalScheme\" />\n";
    final String END =
      "  </metaInfo>\n" +
      "</scheme>";

    boolean nonDefaultRainbow = !RainbowHighlighter.DEFAULT_RAINBOW_ON;
    
    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, null, nonDefaultRainbow);
    assertTrue(RainbowHighlighter.isRainbowEnabled(editorColorsScheme, null) == nonDefaultRainbow);
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));
    
    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, Language.ANY, nonDefaultRainbow);
    assertTrue(RainbowHighlighter.isRainbowEnabled(editorColorsScheme, Language.ANY) == nonDefaultRainbow);
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow " + Language.ANY.getID() + "\">" + nonDefaultRainbow + "</property>\n" +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, Language.ANY, null);
    assertNull(RainbowHighlighter.isRainbowEnabled(editorColorsScheme, Language.ANY));
    assertTrue(RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, Language.ANY) == nonDefaultRainbow);
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, null, RainbowHighlighter.DEFAULT_RAINBOW_ON);
    assertTrue(RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, null) == RainbowHighlighter.DEFAULT_RAINBOW_ON);
    assertTrue(RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, Language.ANY) == RainbowHighlighter.DEFAULT_RAINBOW_ON);
    assertXmlOutputEquals(
      BEGIN + END,
      serializeWithFixedMeta(editorColorsScheme));
  }

  public void testSettingsEqual() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("Test");
    editorColorsScheme.setColor(EditorColors.TEARLINE_COLOR, new Color(255, 0, 0));
    assertFalse(editorColorsScheme.settingsEqual(defaultScheme));
  }

  public void testReadFontPreferences() throws Exception {
    String name1 = getExistingNonDefaultFontName();
    String name2 = getAnotherExistingNonDefaultFontName();
    EditorColorsScheme scheme = loadScheme(
      "<scheme name=\"fira\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <option name=\"LINE_SPACING\" value=\"0.93\" />\n" +
      "  <font>\n" +
      "    <option name=\"EDITOR_FONT_NAME\" value=\"" + name1 + "\" />\n" +
      "    <option name=\"EDITOR_FONT_SIZE\" value=\"12\" />\n" +
      "  </font>\n" +
      "  <font>\n" +
      "    <option name=\"EDITOR_FONT_NAME\" value=\"" + name2 + "\" />\n" +
      "    <option name=\"EDITOR_FONT_SIZE\" value=\"12\" />\n" +
      "  </font>\n" +
      "  <option name=\"EDITOR_LIGATURES\" value=\"true\" />\n" +
      "  <option name=\"CONSOLE_FONT_NAME\" value=\""+ name2 + "\" />" +
      "</scheme>\n"
    );
    assertEquals(name1, scheme.getEditorFontName());
    assertEquals(name2, scheme.getConsoleFontName());
    assertEquals(0.93f, scheme.getLineSpacing());
    assertTrue(scheme.getFontPreferences().useLigatures());
    assertFalse(scheme.getConsoleFontPreferences().useLigatures());
  }

  public void testReadFontPreferencesIdea176762() throws Exception {
    String fontName = getExistingNonDefaultFontName();
    EditorColorsScheme scheme = loadScheme(
      "<scheme name=\"_@user_Default\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <option name=\"FONT_SCALE\" value=\"1.5\" />\n" +
      "  <option name=\"EDITOR_FONT_SIZE\" value=\"18\" />\n" +
      "  <option name=\"EDITOR_LIGATURES\" value=\"true\" />\n" +
      "  <option name=\"EDITOR_FONT_NAME\" value=\"" + fontName + "\" />\n" +
      "</scheme>"
    );
    assertEquals(fontName, scheme.getEditorFontName());
    assertTrue("Expected font ligatures on", scheme.getFontPreferences().useLigatures());
  }

  public void testOptimizeAttributes() throws Exception {
    TextAttributesKey staticFieldKey = TextAttributesKey.createTextAttributesKey("STATIC_FIELD_ATTRIBUTES");
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)loadScheme(
      "<scheme name=\"IdeaLight\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <colors>\n" +
      "    <option name=\"CARET_ROW_COLOR\" value=\"f5f5f5\" />\n" +
      "    <option name=\"CONSOLE_BACKGROUND_KEY\" value=\"fdfdfd\" />\n" +
      "  </colors>\n" +
      "  <attributes>\n" +
      "    <option name=\"DEFAULT_ATTRIBUTE\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"4c4fa1\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"1\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_CLASS_NAME\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"906f5d\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_CONSTANT\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"776186\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"3\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_FUNCTION_DECLARATION\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"707070\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_GLOBAL_VARIABLE\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"6e6cc2\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"1\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_IDENTIFIER\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"707070\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_INSTANCE_FIELD\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"776186\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_INTERFACE_NAME\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"906f5d\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"2\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_KEYWORD\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"707070\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"1\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_LOCAL_VARIABLE\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"6f8374\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_METADATA\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"989800\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_NUMBER\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"8281e8\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_OPERATION_SIGN\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"9587a4\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_PARAMETER\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"a05f72\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_PARENTHS\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"7e7e7e\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_PREDEFINED_SYMBOL\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"ab8381\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"2\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_SEMICOLON\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"9587a4\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_STATIC_FIELD\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"776186\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"2\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_STATIC_METHOD\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"707070\" />\n" +
      "        <option name=\"FONT_TYPE\" value=\"2\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"DEFAULT_STRING\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"58806b\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "    <option name=\"INSTANCE_FIELD_ATTRIBUTES\" baseAttributes=\"DEFAULT_INSTANCE_FIELD\" />\n" +
      "    <option name=\"STATIC_FIELD_ATTRIBUTES\" baseAttributes=\"DEFAULT_STATIC_FIELD\" />\n" +
      "    <option name=\"STATIC_FINAL_FIELD_ATTRIBUTES\" baseAttributes=\"STATIC_FIELD_ATTRIBUTES\" />\n" +
      "    <option name=\"TEXT\">\n" +
      "      <value>\n" +
      "        <option name=\"FOREGROUND\" value=\"141414\" />\n" +
      "        <option name=\"BACKGROUND\" value=\"fbfbfb\" />\n" +
      "      </value>\n" +
      "    </option>\n" +
      "  </attributes>\n" +
      "</scheme>"
    );
    editorColorsScheme.optimizeAttributeMap();
    //
    // The following attributes have specific colors in Default color scheme. It is important to keep the inheritance markers, otherwise
    // the explicitly defined colors from the base (default) scheme will be used which is not what we want here.
    //
    assertSame(INHERITED_ATTRS_MARKER, editorColorsScheme.getDirectlyDefinedAttributes(staticFieldKey));
  }

  public void testIdea188308() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme initialScheme = (EditorColorsScheme)defaultScheme.clone();
    initialScheme.setLineSpacing(1.2f);
    initialScheme.setConsoleLineSpacing(1.0f);
    assertFalse(initialScheme.getLineSpacing() == initialScheme.getConsoleLineSpacing());
    Element root = serialize(initialScheme);
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);
    targetScheme.readExternal(root);
    assertEquals(1.0f, targetScheme.getConsoleLineSpacing());
  }

  public void testNonDefaultConsoleLineSpacing() {
    ModifiableFontPreferences appPrefs = (ModifiableFontPreferences)AppEditorFontOptions.getInstance().getFontPreferences();
    float currSpacing = appPrefs.getLineSpacing();
    try {
      appPrefs.setLineSpacing(1.2f);
      EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
      EditorColorsScheme initialScheme = (EditorColorsScheme)defaultScheme.clone();
      initialScheme.setConsoleLineSpacing(1.0f);
      assertFalse(appPrefs.getLineSpacing() == initialScheme.getConsoleLineSpacing());
      Element root = serialize(initialScheme);

      EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);
      targetScheme.readExternal(root);
      appPrefs.setLineSpacing(1.3f);

      assertEquals(1.3f, targetScheme.getLineSpacing()); // we expect that editor still listens to app font preferences
      assertEquals(1.0f, targetScheme.getConsoleLineSpacing());
    }
    finally {
      appPrefs.setLineSpacing(currSpacing);
    }
  }
}
