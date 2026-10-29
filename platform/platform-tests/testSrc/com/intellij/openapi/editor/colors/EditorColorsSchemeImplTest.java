// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.idea.TestFor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.util.ui.UIUtil;
import org.assertj.core.api.Assertions;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.editor.EditorColorSchemeTestUtil.assertXmlOutputEquals;
import static com.intellij.editor.EditorColorSchemeTestUtil.doTestWriteRead;
import static com.intellij.editor.EditorColorSchemeTestUtil.loadScheme;
import static com.intellij.editor.EditorColorSchemeTestUtil.serialize;
import static com.intellij.editor.EditorColorSchemeTestUtil.serializeWithFixedMeta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class EditorColorsSchemeImplTest {
  private EditorColorsSchemeImpl myScheme;

  @TestDisposable
  private Disposable testRootDisposable;

  @BeforeEach
  void setUp() {
    myScheme = new EditorColorsSchemeImpl(null);
  }

  @AfterEach
  void tearDown() {
    myScheme = null;
  }

  @Test
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

  private static String substLinuxFontName(@NotNull String fontName) {
    return SystemInfo.isLinux && GraphicsEnvironment.isHeadless() && FontPreferences.LINUX_DEFAULT_FONT_FAMILY.equals(fontName) ?
           FontPreferences.FALLBACK_FONT_FAMILY :
           fontName;
  }

  private void assertEditorFontsEqual(@NotNull String fontName, int fontSize) {
    assertEquals(fontName, substLinuxFontName(myScheme.getEditorFontName()));
    assertEquals(fontSize, myScheme.getEditorFontSize());
    assertEquals(fontName, substLinuxFontName(myScheme.getConsoleFontName()));
    assertEquals(fontSize, myScheme.getConsoleFontSize());
  }

  @Test
  public void testDefaults() {
    myScheme.setFontPreferences(new FontPreferencesImpl());
    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   FontPreferences.DEFAULT_FONT_NAME,
                                   FontPreferences.DEFAULT_FONT_NAME, null);
    String expectedName = FontPreferences.DEFAULT_FONT_NAME;
    assertEquals(expectedName, myScheme.getEditorFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getEditorFontSize());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   FontPreferences.DEFAULT_FONT_NAME,
                                   FontPreferences.DEFAULT_FONT_NAME, null);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getConsoleFontName());
    assertEquals(FontPreferences.DEFAULT_FONT_SIZE, myScheme.getConsoleFontSize());
  }

  @Test
  public void testSetFontPreferences() {
    String fontName1 = FontPreferencesTest.getExistingNonDefaultFontName();
    String fontName2 = FontPreferencesTest.getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    FontPreferences fontPreferences = myScheme.getFontPreferences();
    assertInstanceOf(ModifiableFontPreferences.class, fontPreferences);
    ((ModifiableFontPreferences)fontPreferences).register(fontName1, 25);
    ((ModifiableFontPreferences)fontPreferences).register(fontName2, 13);
    FontPreferences consoleFontPreferences = myScheme.getConsoleFontPreferences();
    assertInstanceOf(FontPreferences.class, consoleFontPreferences);
    myScheme.setConsoleFontSize(10);
    consoleFontPreferences = myScheme.getConsoleFontPreferences();
    assertInstanceOf(ModifiableFontPreferences.class, consoleFontPreferences);
    ((ModifiableFontPreferences)consoleFontPreferences).register(fontName1, 21);
    ((ModifiableFontPreferences)consoleFontPreferences).register(fontName2, 15);

    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Arrays.asList(fontName1, fontName2),
                                   Arrays.asList(fontName1, fontName2),
                                   fontName1,
                                   fontName1, 25,
                                   fontName2, 13);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Arrays.asList(fontName1, fontName2),
                                   Arrays.asList(fontName1, fontName2),
                                   fontName1,
                                   fontName1, 21,
                                   fontName2, 15);
    assertEquals(fontName1, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());

    myScheme.setUseEditorFontPreferencesInConsole();
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Arrays.asList(fontName1, fontName2),
                                   Arrays.asList(fontName1, fontName2),
                                   fontName1,
                                   fontName1, 25,
                                   fontName2, 13);

  }

  @Test
  public void testSetName() {
    String fontName1 = FontPreferencesTest.getExistingNonDefaultFontName();
    String fontName2 = FontPreferencesTest.getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    myScheme.setConsoleFontName(fontName2);
    float scaledSize = UISettings.restoreFontSize((float)FontPreferences.DEFAULT_FONT_SIZE, 1.0f);

    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Collections.singletonList(fontName1),
                                   Collections.singletonList(fontName1),
                                   fontName1,
                                   fontName1, scaledSize);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(scaledSize, myScheme.getEditorFontSize2D());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Collections.singletonList(fontName2),
                                   Collections.singletonList(fontName2),
                                   fontName2,
                                   fontName2, scaledSize);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(scaledSize, myScheme.getConsoleFontSize2D());
  }

  @Test
  public void testSetSize() {
    myScheme.setEditorFontSize(25);
    myScheme.setConsoleFontSize(21);

    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Collections.singletonList(FontPreferences.DEFAULT_FONT_NAME),
                                   Collections.singletonList(FontPreferences.DEFAULT_FONT_NAME),
                                   FontPreferences.DEFAULT_FONT_NAME,
                                   FontPreferences.DEFAULT_FONT_NAME, 25);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Collections.singletonList(FontPreferences.DEFAULT_FONT_NAME),
                                   Collections.singletonList(FontPreferences.DEFAULT_FONT_NAME),
                                   FontPreferences.DEFAULT_FONT_NAME,
                                   FontPreferences.DEFAULT_FONT_NAME, 21);
    assertEquals(FontPreferences.DEFAULT_FONT_NAME, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  @Test
  public void testSetNameAndSize() {
    String fontName1 = FontPreferencesTest.getExistingNonDefaultFontName();
    String fontName2 = FontPreferencesTest.getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontName(fontName1);
    myScheme.setEditorFontSize(25);
    myScheme.setConsoleFontName(fontName2);
    myScheme.setConsoleFontSize(21);

    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Collections.singletonList(fontName1),
                                   Collections.singletonList(fontName1),
                                   fontName1,
                                   fontName1, 25);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Collections.singletonList(fontName2),
                                   Collections.singletonList(fontName2),
                                   fontName2,
                                   fontName2, 21);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  @Test
  public void testSetSizeAndName() {
    String fontName1 = FontPreferencesTest.getExistingNonDefaultFontName();
    String fontName2 = FontPreferencesTest.getAnotherExistingNonDefaultFontName();
    myScheme.setEditorFontSize(25);
    myScheme.setEditorFontName(fontName1);
    myScheme.setConsoleFontSize(21);
    myScheme.setConsoleFontName(fontName2);

    FontPreferencesTest.checkState(myScheme.getFontPreferences(),
                                   Collections.singletonList(fontName1),
                                   Collections.singletonList(fontName1),
                                   fontName1,
                                   fontName1, 25);
    assertEquals(fontName1, myScheme.getEditorFontName());
    assertEquals(25, myScheme.getEditorFontSize());
    FontPreferencesTest.checkState(myScheme.getConsoleFontPreferences(),
                                   Collections.singletonList(fontName2),
                                   Collections.singletonList(fontName2),
                                   fontName2,
                                   fontName2, 21);
    assertEquals(fontName2, myScheme.getConsoleFontName());
    assertEquals(21, myScheme.getConsoleFontSize());
  }

  @Test
  public void testWriteColorWithAlpha() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme scheme = (EditorColorsScheme)defaultScheme.clone();
    scheme.setName("test");
    scheme.setColor(ColorKey.createColorKey("BASE_COLOR"), new Color(0x80, 0x81, 0x82));
    scheme.setColor(ColorKey.createColorKey("ALPHA_COLOR"), new Color(0x80, 0x81, 0x82, 0x83));
    assertXmlOutputEquals(
      """
        <scheme name="test" version="142" parent_scheme="Default">
          <colors>
            <option name="ALPHA_COLOR" value="80818283" />
            <option name="BASE_COLOR" value="808182" />
          </colors>
        </scheme>""",
      serialize(scheme));
  }

  @TestFor(issues = "IJPL-223521")
  @Test
  public void testWriteTextAttributesWithAlpha() {
    enableTextAttributesTransparency();
    assertXmlOutputEquals(
      """
        <scheme name="test" version="142" parent_scheme="Default">
          <attributes>
            <option name="TEST_TRANSPARENT_TEXT">
              <value>
                <option name="FOREGROUND" value="af1d1d80" />
                <option name="BACKGROUND" value="808182" />
              </value>
            </option>
          </attributes>
        </scheme>""",
      serialize(schemeWithTransparentText()));
  }

  /**
   * With {@code editor.text.attributes.transparency} off the scheme must be written exactly as it was before
   * transparency support, so that turning the key on is the only thing that changes the saved format.
   */
  @TestFor(issues = "IJPL-223521")
  @Test
  public void testWriteTextAttributesWithAlphaWhenTransparencyDisabled() {
    assertFalse(Registry.is("editor.text.attributes.transparency"));
    assertXmlOutputEquals(
      """
        <scheme name="test" version="142" parent_scheme="Default">
          <attributes>
            <option name="TEST_TRANSPARENT_TEXT">
              <value>
                <option name="FOREGROUND" value="af1d1d" />
                <option name="BACKGROUND" value="808182" />
              </value>
            </option>
          </attributes>
        </scheme>""",
      serialize(schemeWithTransparentText()));
  }

  @TestFor(issues = "IJPL-223521")
  @Test
  public void testTextAttributesAlphaSurvivesWriteRead() {
    enableTextAttributesTransparency();
    TextAttributesKey key = TextAttributesKey.createTextAttributesKey("TEST_TRANSPARENT_TEXT_ROUND_TRIP");
    TextAttributes attributes = new TextAttributes(new Color(0xAF, 0x1D, 0x1D, 0x80), new Color(0x1D, 0x1D, 0xAF, 0x40),
                                                   null, EffectType.BOXED, Font.PLAIN);
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(key, attributes);
    assertEquals(attributes, result.second);
    assertEquals(0x80, result.second.getForegroundColor().getAlpha());
    assertEquals(0x40, result.second.getBackgroundColor().getAlpha());
  }

  @TestFor(issues = "IJPL-223521")
  @Test
  public void testTextAttributesAlphaDroppedWhenTransparencyDisabled() {
    assertFalse(Registry.is("editor.text.attributes.transparency"));
    TextAttributesKey key = TextAttributesKey.createTextAttributesKey("TEST_OPAQUE_TEXT_ROUND_TRIP");
    TextAttributes attributes = new TextAttributes(new Color(0xAF, 0x1D, 0x1D, 0x80), null,
                                                   null, EffectType.BOXED, Font.PLAIN);
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(key, attributes);
    assertEquals(new Color(0xAF, 0x1D, 0x1D), result.second.getForegroundColor());
  }

  @Test
  public void testTextAttributesFontFamiliesSurviveWriteRead() {
    TextAttributesKey key = TextAttributesKey.createTextAttributesKey("TEST_FONT_FAMILIES_ROUND_TRIP");
    TextAttributes attributes = new TextAttributes(new Color(0x112233), null, null, EffectType.BOXED, Font.PLAIN);
    attributes.setFontFamilies(FontFamilies.of("JetBrains Mono", "Noto Sans CJK JP"));

    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(key, attributes);

    assertEquals(attributes, result.second);
  }

  @Test
  public void testLayeredTextAttributesKeepFontFamilies() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme scheme = (EditorColorsScheme)defaultScheme.clone();
    TextAttributesKey under = TextAttributesKey.createTextAttributesKey("TEST_LAYERED_FONT_FAMILIES_UNDER");
    TextAttributesKey above = TextAttributesKey.createTextAttributesKey("TEST_LAYERED_FONT_FAMILIES_ABOVE");
    TextAttributes underAttributes = new TextAttributes(new Color(0x112233), null, null, null, Font.PLAIN);
    underAttributes.setFontFamilies(FontFamilies.of("JetBrains Mono"));
    scheme.setAttributes(under, underAttributes);
    scheme.setAttributes(above, new TextAttributes(null, new Color(0x445566), null, null, Font.BOLD));

    LayeredTextAttributes layered = LayeredTextAttributes.create(scheme, new TextAttributesKey[]{under, above});

    assertEquals(FontFamilies.of("JetBrains Mono"), layered.getFontFamilies());
    assertEquals(new Color(0x112233), layered.getForegroundColor());
    assertEquals(new Color(0x445566), layered.getBackgroundColor());
    assertEquals(Font.BOLD, layered.getFontType());
  }

  private void enableTextAttributesTransparency() {
    Registry.get("editor.text.attributes.transparency").setValue(true, testRootDisposable);
  }

  private static @NotNull EditorColorsScheme schemeWithTransparentText() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme scheme = (EditorColorsScheme)defaultScheme.clone();
    scheme.setName("test");
    scheme.setAttributes(TextAttributesKey.createTextAttributesKey("TEST_TRANSPARENT_TEXT"),
                         new TextAttributes(new Color(0xAF, 0x1D, 0x1D, 0x80), new Color(0x80, 0x81, 0x82),
                                            null, EffectType.BOXED, Font.PLAIN));
    return scheme;
  }

  @Test
  public void testWriteInheritedFromDefault() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("test");
    assertXmlOutputEquals(
      "<scheme name=\"test\" version=\"142\" parent_scheme=\"Default\" />",
      serialize(editorColorsScheme));

    String fontName = editorColorsScheme.getEditorFontName();

    editorColorsScheme.setConsoleFontName(fontName);
    editorColorsScheme.setConsoleFontSize(10);
    assertXmlOutputEquals(
      """
        <scheme name="test" version="142" parent_scheme="Default">
          <option name="CONSOLE_FONT_NAME" value="Test" />
          <option name="CONSOLE_FONT_SIZE" value="10" />
          <option name="CONSOLE_LINE_SPACING" value="1.2" />
        </scheme>""",
      serialize(editorColorsScheme));
  }

  @Test
  public void testWriteInheritedFromDarcula() {
    EditorColorsScheme darculaScheme = EditorColorsManager.getInstance().getScheme("Darcula");
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)darculaScheme.clone();
    editorColorsScheme.setName("test");
    assertXmlOutputEquals(
      "<scheme name=\"test\" version=\"142\" parent_scheme=\"Darcula\" />",
      serialize(editorColorsScheme));
  }


  @Test
  public void testSaveInheritance() {
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.STATIC_METHOD, AbstractColorsScheme.INHERITED_ATTRS_MARKER);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.STATIC_METHOD.getFallbackAttributeKey());
    assertSame(result.second, fallbackAttrs);
  }

  @Test
  public void testSaveNoInheritanceAndDefaults() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    TextAttributes declarationAttrs = defaultScheme.getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER).clone();
    assertEquals(DefaultLanguageHighlighterColors.IDENTIFIER, DefaultLanguageHighlighterColors.FUNCTION_DECLARATION.getFallbackAttributeKey());
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, declarationAttrs);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION.getFallbackAttributeKey());
    Assertions.assertThat(result.second).isNotSameAs(fallbackAttrs);
    Assertions.assertThat(ExpectedHighlightingData.sameTextAttributesByValue(result.second, fallbackAttrs)).isTrue();
  }

  @Test
  public void testSaveInheritanceForEmptyAttrs() {
    Pair<EditorColorsScheme, TextAttributes> result = doTestWriteRead(DefaultLanguageHighlighterColors.INSTANCE_FIELD, AbstractColorsScheme.INHERITED_ATTRS_MARKER);
    TextAttributes fallbackAttrs = result.first.getAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD.getFallbackAttributeKey());
    TextAttributes directlyDefined =
      ((AbstractColorsScheme)result.first).getDirectlyDefinedAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    assertTrue(directlyDefined == AbstractColorsScheme.INHERITED_ATTRS_MARKER);
    assertSame(fallbackAttrs, result.second);
  }

  @Test
  public void testUpgradeFromVer141() throws Exception {
    TextAttributesKey constKey = DefaultLanguageHighlighterColors.CONSTANT;
    TextAttributesKey fallbackKey = constKey.getFallbackAttributeKey();
    assertNotNull(fallbackKey);

    EditorColorsScheme scheme = loadScheme(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <scheme name="Test" version="141" parent_scheme="Default">
        <attributes>   <option name="TEXT">
              <value>
                   option name="FOREGROUND" value="ffaaaa" />
              </value>
           </option></attributes></scheme>
        """
    );

    TextAttributes constAttrs = scheme.getAttributes(constKey);
    TextAttributes fallbackAttrs = scheme.getAttributes(fallbackKey);
    assertNotSame(fallbackAttrs, constAttrs);
    assertEquals(Font.BOLD | Font.ITALIC, constAttrs.getFontType());

    TextAttributes classAttrs = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME);
    TextAttributes classFallbackAttrs = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME.getFallbackAttributeKey());
    assertSame(classFallbackAttrs, classAttrs);
  }

  @Test
  public void testPreventCyclicTextAttributeDependency() {
    DefaultLogger.disableStderrDumping(testRootDisposable);
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("test");
    TextAttributesKey keyD = TextAttributesKey.createTextAttributesKey("D");
    TextAttributesKey keyC = TextAttributesKey.createTextAttributesKey("C", keyD);
    TextAttributesKey keyB = TextAttributesKey.createTextAttributesKey("B", keyC);
    TextAttributesKey keyA = TextAttributesKey.createTextAttributesKey("A", keyB);
    try {
      keyD = TextAttributesKey.createTextAttributesKey("D", keyB);
      fail("Must fail");
    }
    catch (IllegalArgumentException e) {
      String s = e.getMessage();
      assertTrue(s.matches(".*B.*->C.*->.*D.*"), s);

      try {
        editorColorsScheme.getAttributes(keyA);
      }
      catch (StackOverflowError ignored) {
        fail("Stack overflow detected!");
      }
    }
    finally {
      TextAttributesKey.removeTextAttributesKey(keyA.getExternalName());
      TextAttributesKey.removeTextAttributesKey(keyB.getExternalName());
      TextAttributesKey.removeTextAttributesKey(keyC.getExternalName());
      TextAttributesKey.removeTextAttributesKey(keyD.getExternalName());
    }
  }

  @Test
  public void testMustNotBePossibleToRegisterTextAttributeKeysWithDifferentFallBacks() throws Exception {
    DefaultLogger.disableStderrDumping(testRootDisposable);
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      TextAttributesKey keyB = TextAttributesKey.createTextAttributesKey("B");
      TextAttributesKey keyD = TextAttributesKey.createTextAttributesKey("D");
      TextAttributesKey keyC = TextAttributesKey.createTextAttributesKey("C", keyD);
      try {
        keyC = TextAttributesKey.createTextAttributesKey(keyC.getExternalName(), keyB);
        fail("Must fail");
      }
      catch (IllegalStateException | AssertionError e) {
        assertTrue(e.getMessage().contains("already registered"));
      }
      finally {
        TextAttributesKey.removeTextAttributesKey(keyB.getExternalName());
        TextAttributesKey.removeTextAttributesKey(keyC.getExternalName());
        TextAttributesKey.removeTextAttributesKey(keyD.getExternalName());
      }
    });
  }

  @Test
  public void testIdea152156() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme parentScheme = (EditorColorsScheme)defaultScheme.clone();
    parentScheme.setName("DefaultTest");
    AbstractColorsScheme editorColorsScheme = new EditorColorsSchemeImpl(parentScheme);
    editorColorsScheme.setName("test");
    TextAttributes defaultAttributes = new TextAttributes(null, null, Color.BLACK, EffectType.LINE_UNDERSCORE, Font.PLAIN);
    TextAttributesKey testKey = TextAttributesKey.createTextAttributesKey("TEST_KEY", DefaultLanguageHighlighterColors.PARAMETER);
    parentScheme.setAttributes(testKey, defaultAttributes);
    editorColorsScheme.setAttributes(testKey, AbstractColorsScheme.INHERITED_ATTRS_MARKER);
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
      assertSame(AbstractColorsScheme.INHERITED_ATTRS_MARKER, targetAttributes);
    }
    finally {
      TextAttributesKey.removeTextAttributesKey(testKey.getExternalName());
    }
  }

  @TestFor(issues = "IJPL-26971")
  @Test
  public void testTransparencyHexPadding() {
    // Opacity gets stored in color schemes without padding.
    var testColor = new Color(0x88, 0x99, 0xAA, 0x01);
    ensureColorRoundTrips(testColor);
  }

  @TestFor(issues = "IJPL-26971")
  @Test
  public void testColorZeroPadding() {
    // Another consequence of IJPL-26971: the color components also lose paddings, which would break if the opacity component is not FF.
    var testColor = new Color(0x00, 0x99, 0xAA, 0x00);
    ensureColorRoundTrips(testColor);
  }

  private static void ensureColorRoundTrips(Color color) {
    var defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    var parentScheme = (EditorColorsScheme)defaultScheme.clone();
    var editorColorsScheme = new EditorColorsSchemeImpl(parentScheme);
    editorColorsScheme.setName("testEditorColorsScheme");

    var testColorKey = ColorKey.createColorKey("testColorKey");
    editorColorsScheme.setColor(testColorKey, color);

    var root = new Element("scheme");
    editorColorsScheme.writeExternal(root);
    var targetScheme = new EditorColorsSchemeImpl(parentScheme);
    targetScheme.readExternal(root);
    var targetColor = targetScheme.getColor(testColorKey);
    assertEquals(color, targetColor);
  }

  @Test
  public void testWriteDefaultSemanticHighlighting() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("rainbow");

    final String BEGIN =
      """
        <scheme name="rainbow" version="142" parent_scheme="Default">
          <metaInfo>
            <property name="ide" />
            <property name="ideVersion" />
            <property name="originalScheme" />
        """;
    final String END =
      "  </metaInfo>\n" +
      "</scheme>";

    boolean nonDefaultRainbow = !RainbowHighlighter.DEFAULT_RAINBOW_ON;

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, null, nonDefaultRainbow);
    assertEquals(nonDefaultRainbow, RainbowHighlighter.isRainbowEnabled(editorColorsScheme, null));
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, Language.ANY, nonDefaultRainbow);
    assertEquals(nonDefaultRainbow, RainbowHighlighter.isRainbowEnabled(editorColorsScheme, Language.ANY));
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow " + Language.ANY.getID() + "\">" + nonDefaultRainbow + "</property>\n" +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, Language.ANY, null);
    assertNull(RainbowHighlighter.isRainbowEnabled(editorColorsScheme, Language.ANY));
    assertEquals(nonDefaultRainbow, RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, Language.ANY));
    assertXmlOutputEquals(
      BEGIN +
      "    <property name=\"rainbow Default language\">" + nonDefaultRainbow + "</property>\n" +
      END,
      serializeWithFixedMeta(editorColorsScheme));

    RainbowHighlighter.setRainbowEnabled(editorColorsScheme, null, RainbowHighlighter.DEFAULT_RAINBOW_ON);
    assertEquals(RainbowHighlighter.DEFAULT_RAINBOW_ON,
                 RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, null));
    assertEquals(RainbowHighlighter.DEFAULT_RAINBOW_ON,
                 RainbowHighlighter.isRainbowEnabledWithInheritance(editorColorsScheme, Language.ANY));
    assertXmlOutputEquals(
      BEGIN + END,
      serializeWithFixedMeta(editorColorsScheme));
  }

  @Test
  public void testSettingsEqual() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)defaultScheme.clone();
    editorColorsScheme.setName("Test");
    editorColorsScheme.setColor(EditorColors.TEARLINE_COLOR, new Color(255, 0, 0));
    assertFalse(editorColorsScheme.settingsEqual(defaultScheme));
  }

  @Test
  public void testSettingsEqualIgnoresMetaInfo() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    AbstractColorsScheme scheme = (AbstractColorsScheme)defaultScheme.clone();
    AbstractColorsScheme otherScheme = (AbstractColorsScheme)defaultScheme.clone();
    scheme.getMetaProperties().setProperty(AbstractColorsScheme.META_INFO_PLUGIN_ID, "test.plugin");
    assertFalse(scheme.settingsEqual(otherScheme, null, false));
    assertTrue(scheme.settingsEqual(otherScheme, null, true));
  }

  @Test
  public void testReadFontPreferences() throws Exception {
    String name1 = FontPreferencesTest.getExistingNonDefaultFontName();
    String name2 = FontPreferencesTest.getAnotherExistingNonDefaultFontName();
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

  @Test
  public void testReadFontPreferencesIdea176762() throws Exception {
    String fontName = FontPreferencesTest.getExistingNonDefaultFontName();
    EditorColorsScheme scheme = loadScheme(
      "<scheme name=\"_@user_Default\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <option name=\"FONT_SCALE\" value=\"1.5\" />\n" +
      "  <option name=\"EDITOR_FONT_SIZE\" value=\"18\" />\n" +
      "  <option name=\"EDITOR_LIGATURES\" value=\"true\" />\n" +
      "  <option name=\"EDITOR_FONT_NAME\" value=\"" + fontName + "\" />\n" +
      "</scheme>"
    );
    assertEquals(fontName, scheme.getEditorFontName());
    assertTrue(scheme.getFontPreferences().useLigatures(), "Expected font ligatures on");
  }

  @Test
  public void testOptimizeAttributes() throws Exception {
    TextAttributesKey staticFieldKey = TextAttributesKey.createTextAttributesKey("STATIC_FIELD_ATTRIBUTES");
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)loadScheme(
      """
        <scheme name="IdeaLight" version="142" parent_scheme="Default">
          <colors>
            <option name="CARET_ROW_COLOR" value="f5f5f5" />
            <option name="CONSOLE_BACKGROUND_KEY" value="fdfdfd" />
          </colors>
          <attributes>
            <option name="DEFAULT_ATTRIBUTE">
              <value>
                <option name="FOREGROUND" value="4c4fa1" />
                <option name="FONT_TYPE" value="1" />
              </value>
            </option>
            <option name="DEFAULT_CLASS_NAME">
              <value>
                <option name="FOREGROUND" value="906f5d" />
              </value>
            </option>
            <option name="DEFAULT_CONSTANT">
              <value>
                <option name="FOREGROUND" value="776186" />
                <option name="FONT_TYPE" value="3" />
              </value>
            </option>
            <option name="DEFAULT_FUNCTION_DECLARATION">
              <value>
                <option name="FOREGROUND" value="707070" />
              </value>
            </option>
            <option name="DEFAULT_GLOBAL_VARIABLE">
              <value>
                <option name="FOREGROUND" value="6e6cc2" />
                <option name="FONT_TYPE" value="1" />
              </value>
            </option>
            <option name="DEFAULT_IDENTIFIER">
              <value>
                <option name="FOREGROUND" value="707070" />
              </value>
            </option>
            <option name="DEFAULT_INSTANCE_FIELD">
              <value>
                <option name="FOREGROUND" value="776186" />
              </value>
            </option>
            <option name="DEFAULT_INTERFACE_NAME">
              <value>
                <option name="FOREGROUND" value="906f5d" />
                <option name="FONT_TYPE" value="2" />
              </value>
            </option>
            <option name="DEFAULT_KEYWORD">
              <value>
                <option name="FOREGROUND" value="707070" />
                <option name="FONT_TYPE" value="1" />
              </value>
            </option>
            <option name="DEFAULT_LOCAL_VARIABLE">
              <value>
                <option name="FOREGROUND" value="6f8374" />
              </value>
            </option>
            <option name="DEFAULT_METADATA">
              <value>
                <option name="FOREGROUND" value="989800" />
              </value>
            </option>
            <option name="DEFAULT_NUMBER">
              <value>
                <option name="FOREGROUND" value="8281e8" />
              </value>
            </option>
            <option name="DEFAULT_OPERATION_SIGN">
              <value>
                <option name="FOREGROUND" value="9587a4" />
              </value>
            </option>
            <option name="DEFAULT_PARAMETER">
              <value>
                <option name="FOREGROUND" value="a05f72" />
              </value>
            </option>
            <option name="DEFAULT_PARENTHS">
              <value>
                <option name="FOREGROUND" value="7e7e7e" />
              </value>
            </option>
            <option name="DEFAULT_PREDEFINED_SYMBOL">
              <value>
                <option name="FOREGROUND" value="ab8381" />
                <option name="FONT_TYPE" value="2" />
              </value>
            </option>
            <option name="DEFAULT_SEMICOLON">
              <value>
                <option name="FOREGROUND" value="9587a4" />
              </value>
            </option>
            <option name="DEFAULT_STATIC_FIELD">
              <value>
                <option name="FOREGROUND" value="776186" />
                <option name="FONT_TYPE" value="2" />
              </value>
            </option>
            <option name="DEFAULT_STATIC_METHOD">
              <value>
                <option name="FOREGROUND" value="707070" />
                <option name="FONT_TYPE" value="2" />
              </value>
            </option>
            <option name="DEFAULT_STRING">
              <value>
                <option name="FOREGROUND" value="58806b" />
              </value>
            </option>
            <option name="INSTANCE_FIELD_ATTRIBUTES" baseAttributes="DEFAULT_INSTANCE_FIELD" />
            <option name="STATIC_FIELD_ATTRIBUTES" baseAttributes="DEFAULT_STATIC_FIELD" />
            <option name="STATIC_FINAL_FIELD_ATTRIBUTES" baseAttributes="STATIC_FIELD_ATTRIBUTES" />
            <option name="TEXT">
              <value>
                <option name="FOREGROUND" value="141414" />
                <option name="BACKGROUND" value="fbfbfb" />
              </value>
            </option>
          </attributes>
        </scheme>"""
    );
    editorColorsScheme.optimizeAttributeMap();
    //
    // The following attributes have specific colors in Default color scheme. It is important to keep the inheritance markers, otherwise
    // the explicitly defined colors from the base (default) scheme will be used which is not what we want here.
    //
    assertSame(AbstractColorsScheme.INHERITED_ATTRS_MARKER, editorColorsScheme.getDirectlyDefinedAttributes(staticFieldKey));
  }

  @Test
  public void testInheritedElementWithoutFallback() throws Exception {
    TextAttributesKey TEST_KEY = TextAttributesKey.createTextAttributesKey("TEST_ATTRIBUTE_KEY", DefaultLanguageHighlighterColors.KEYWORD);
    try {
      AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)loadScheme(
        """
          <scheme name="Super Scheme" parent_scheme="Darcula" version="1">
            <attributes>
              <option name="DEFAULT_KEYWORD" baseAttributes="TEXT" />
              <option name="TEST_ATTRIBUTE_KEY" baseAttributes="TEXT" />
            </attributes></scheme>""");
      TextAttributes originalAttributes = editorColorsScheme.getAttributes(TEST_KEY);

      Element dumpedDom = editorColorsScheme.writeScheme();
      AbstractColorsScheme reloadedScheme = (AbstractColorsScheme)loadScheme(JDOMUtil.writeElement(dumpedDom));

      assertEquals(originalAttributes, reloadedScheme.getAttributes(TEST_KEY));
    }
    finally {
      TextAttributesKey.removeTextAttributesKey("TEST_ATTRIBUTE_KEY");
    }
  }

  @Test
  public void testIdea188308() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme initialScheme = (EditorColorsScheme)defaultScheme.clone();
    initialScheme.setLineSpacing(1.2f);
    initialScheme.setConsoleLineSpacing(1.0f);
    assertFalse(initialScheme.getLineSpacing() == initialScheme.getConsoleLineSpacing());
    Element root = serialize(initialScheme);
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);
    targetScheme.readExternal(root);
    assertEquals(1.0f, targetScheme.getConsoleLineSpacing());
  }

  @Test
  public void testNonDefaultConsoleLineSpacing() {
    ModifiableFontPreferences appPrefs = (ModifiableFontPreferences)AppEditorFontOptions.getInstance().getFontPreferences();
    float currSpacing = appPrefs.getLineSpacing();
    try {
      appPrefs.setLineSpacing(1.2f);
      EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
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

  @Test
  public void testEa124005() {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.getDefaultSchemeName());
    EditorColorsScheme editorColorsScheme = (EditorColorsScheme)defaultScheme.clone();
    editorColorsScheme.setColor(EditorColors.LINE_NUMBERS_COLOR, null);
    editorColorsScheme.setColor(EditorColors.LINE_NUMBERS_COLOR, new Color(255, 0, 0));
  }

  /**
   * Previously saved schemes containing no "partialSave" attribute are considered to be valid and complete.
   */
  @Test
  public void testMissingBundledSchemeNoException() throws Exception{
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)loadScheme(
      """
        <scheme name="_@user_NonExistentBundled" version="142" parent_scheme="Darcula">
          <metaInfo>
            <property name="originalScheme">NonExistentBundled</property>
          </metaInfo>
          <attributes>
            <option name="TEXT">
              <value>
                <option name="FOREGROUND" value="fcfcfa" />
                <option name="BACKGROUND" value="261b28" />
                <option name="EFFECT_TYPE" value="5" />
              </value>
            </option>
          </attributes>
        </scheme>
        """
    );
    EditorColorsManager.getInstance().resolveSchemeParent(editorColorsScheme);
  }

  @Test
  public void testMissingBundledSchemeError() throws Exception{
    AbstractColorsScheme editorColorsScheme = (AbstractColorsScheme)loadScheme(
      """
        <scheme name="_@user_NonExistentBundled" version="142" parent_scheme="Darcula">
          <metaInfo>
            <property name="originalScheme">NonExistentBundled</property>
            <property name="partialSave">true</property>
          </metaInfo>
          <attributes>
            <option name="TEXT">
              <value>
                <option name="FOREGROUND" value="fcfcfa" />
                <option name="BACKGROUND" value="261b28" />
                <option name="EFFECT_TYPE" value="5" />
              </value>
            </option>
          </attributes>
        </scheme>
        """
    );
    String exceptionMessage = null;
    try {
      EditorColorsManager.getInstance().resolveSchemeParent(editorColorsScheme);
    }
    catch (InvalidDataException ex) {
      exceptionMessage = ex.getMessage();
    }
    assertEquals("NonExistentBundled", exceptionMessage);
  }

  @TestFor(issues = "IJPL-148477")
  @Test
  public void testAnnounceEditableCopy() throws IOException, SAXException, InterruptedException {
    final String fontName = FontPreferencesTest.getExistingNonDefaultFontName();
    EditorColorsScheme userScheme = loadScheme(
      "<scheme name=\"_@user_Default\" version=\"142\" parent_scheme=\"Default\">\n" +
      "  <option name=\"FONT_SCALE\" value=\"1.5\" />\n" +
      "  <option name=\"EDITOR_FONT_SIZE\" value=\"18\" />\n" +
      "  <option name=\"EDITOR_LIGATURES\" value=\"true\" />\n" +
      "  <option name=\"EDITOR_FONT_NAME\" value=\"" + fontName + "\" />\n" +
      "</scheme>"
    );
    EditorColorsManagerImpl editorColorsManagerImpl = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
    SchemeManager<EditorColorsScheme> schemeManager = editorColorsManagerImpl.getSchemeManager();
    schemeManager.addScheme(userScheme, true);
    schemeManager.save();

    try {
      final AtomicBoolean handlerProcessed = new AtomicBoolean();
      ApplicationManager.getApplication().getMessageBus().connect(testRootDisposable).subscribe(EditorColorsManager.TOPIC, scheme -> {
        assertEquals(fontName, scheme.getEditorFontName(), "Should have received actual scheme");
        handlerProcessed.set(true);
      });
      EditorColorsScheme bundledDefault = EditorColorsManager.getInstance().getScheme("Default");
      assertNotSame(fontName, bundledDefault.getEditorFontName(), "Should have have default font name in bundled scheme");
      EditorColorsManager.getInstance().setGlobalScheme(bundledDefault);
      EdtTestUtil.runInEdtAndWait(() -> UIUtil.dispatchAllInvocationEvents());
      assertTrue(handlerProcessed.get(), "Should have processed EditorColorsManager.TOPIC in 50ms");
    }
    finally {
      schemeManager.removeScheme(userScheme);
    }
  }

  @Test
  public void testCopyDoesNotCatastrophicallyWipeOldAttributes() {
    TextAttributesKey tempKey = TextAttributesKey.createTempTextAttributesKey("myxxx", null);

    EditorColorsSchemeImpl scheme = new EditorColorsSchemeImpl(null);

    try {
      TextAttributes attributes = new TextAttributes(new Color(1, 2, 3), new Color(4, 5, 6), new Color(7, 8, 9), EffectType.BOLD_DOTTED_LINE, 5);
      scheme.setAttributes(tempKey, attributes);
      assertEquals(attributes, scheme.getAttributes(tempKey));
      scheme.copyTo(new EditorColorsSchemeImpl(null));
      assertEquals(attributes, scheme.getAttributes(tempKey));
    }
    finally {
      TextAttributesKey.removeTextAttributesKey(tempKey.getExternalName());
    }
  }
}
