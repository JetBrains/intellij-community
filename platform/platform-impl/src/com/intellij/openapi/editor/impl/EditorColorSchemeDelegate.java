// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.colors.impl.EditorFontCacheImpl;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;


final class EditorColorSchemeDelegate extends DelegateColorScheme {

  private static final Logger LOG = Logger.getInstance(EditorColorSchemeDelegate.class);
  private static final float FONT_SIZE_TO_IGNORE = -1f;

  private final EditorImpl myEditor;

  /**
   * 1) Separated FontPreferences interface and implementation
   * 2) Added a flag to inherit console font preferences from editor ones
   * 3) Moved line spacing to FontPreferences
   * Other fixes according to [CR-IC-7467]
   */
  private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();
  private final FontPreferencesImpl myConsoleFontPreferences = new FontPreferencesImpl();

  private final Map<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<>();
  private final Map<ColorKey, Color> myOwnColors = new HashMap<>();

  /**
   * RUBY-6775: Force Editor Text fields to use default color scheme with white background
   */
  private final EditorColorsScheme myCustomGlobalScheme;

  private Map<EditorFontType, Font> myFontsMap;

  /**
   * IDEA-129631 Add support for fractional font sizes
   */
  private float myMaxFontSize = EditorFontsConstants.getMaxEditorFontSize();

  /**
   * IDEA-304086 Fixed: editor font doesn't get updated from settings after exiting Presentation mode
   * <p>
   * Reset editor's scheme fontSize to -1f
   */
  private float myFontSize = FONT_SIZE_TO_IGNORE;
  private float myConsoleFontSize = FONT_SIZE_TO_IGNORE;

  private String myFaceName;

  /**
   * allow setting per-editor line spacing
   */
  private Float myLineSpacing;

  /**
   * IDEA-240654 Ligatures per editor
   */
  private boolean myFontPreferencesAreSetExplicitly;

  /**
   * Support for per-editor overrides of useLigatures without replacing entire FontPreferences (IDEA-268567, IDEA-294744)
   */
  private Boolean myUseLigatures;

  EditorColorSchemeDelegate(@NotNull EditorImpl editor, @Nullable EditorColorsScheme globalScheme) {
    super(globalScheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : globalScheme);
    myEditor = editor;
    myCustomGlobalScheme = globalScheme;
    updateGlobalScheme();
  }

  @Override
  public TextAttributes getAttributes(TextAttributesKey key) {
    if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
    return getDelegate().getAttributes(key);
  }

  @Override
  public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
    if (TextAttributesKey.isTemp(key)) {
      // CPP-36857 CLion Nova: Semantic highlighting settings have no effect
      // - CWM fixes in settings UI
      // - CWM fixes
      // - support local highlighting re-initialization after a scheme setting changes
      getDelegate().setAttributes(key, attributes);
    }
    else {
      myOwnAttributes.put(key, attributes);
    }
  }

  @Override
  public @Nullable Color getColor(ColorKey key) {
    if (myOwnColors.containsKey(key)) {
      return myOwnColors.get(key);
    }
    return getDelegate().getColor(key);
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    if (color == AbstractColorsScheme.INHERITED_COLOR_MARKER) {
      myOwnColors.remove(key);
    }
    else {
      myOwnColors.put(key, color);
    }

    // These two are here because those attributes are cached
    // and I do not want the clients to call editor's reinit settings in this case.
    myEditor.getCaretModel().reinitSettings();
    myEditor.getSelectionModel().reinitSettings();
  }

  @Override
  public int getEditorFontSize() {
    return (int)(getEditorFontSize2D() + 0.5);
  }

  @Override
  public float getEditorFontSize2D() {
    if (myFontPreferencesAreSetExplicitly) {
      return myFontPreferences.getSize2D(myFontPreferences.getFontFamily());
    }
    if (myFontSize == FONT_SIZE_TO_IGNORE) {
      // IDEA-311926 Specify UISettings for UISettingsUtils when necessary
      return UISettingsUtils.getInstance().scaleFontSize(getDelegate().getEditorFontSize2D());
    }
    return myFontSize;
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    setEditorFontSize((float)fontSize);
  }

  @Override
  public void setEditorFontSize(float fontSize) {
    // IDEA-315284 Adjust min and max font size in EditorImpl.MyColorSchemeDelegate
    float originalSize = UISettingsUtils.getInstance().scaleFontSize(getDelegate().getEditorFontSize2D());

    float minSize = Math.min(EditorImpl.MIN_FONT_SIZE, originalSize);
    if (fontSize < minSize) {
      fontSize = minSize;
    }

    float maxSize = Math.max(myMaxFontSize, originalSize);
    if (fontSize > maxSize) {
      fontSize = maxSize;
    }

    if (fontSize == myFontSize) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font size overridden for " + myEditor, new Throwable());
    }
    myFontPreferencesAreSetExplicitly = false;

    if (fontSize == originalSize) {
      myFontSize = FONT_SIZE_TO_IGNORE;
    }
    else {
      myFontSize = fontSize;
    }

    reinitFonts();
    myEditor.reinitSettings(true, false);
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    return !myFontPreferencesAreSetExplicitly && myFontPreferences.getEffectiveFontFamilies().isEmpty()
           ? getDelegate().getFontPreferences() : myFontPreferences;
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    if (myFontPreferencesAreSetExplicitly && Comparing.equal(preferences, myFontPreferences)) return;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font preferences overridden for " + myEditor, new Throwable());
    }
    myFontPreferencesAreSetExplicitly = true;
    myFaceName = null;
    myFontSize = FONT_SIZE_TO_IGNORE;
    preferences.copyTo(myFontPreferences);
    reinitFontsAndSettings();
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
    return myConsoleFontPreferences.getEffectiveFontFamilies().isEmpty() ?
           getDelegate().getConsoleFontPreferences() : myConsoleFontPreferences;
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    if (Comparing.equal(preferences, myConsoleFontPreferences)) return;
    preferences.copyTo(myConsoleFontPreferences);
    reinitFontsAndSettings();
  }

  @Override
  public String getEditorFontName() {
    if (myFontPreferencesAreSetExplicitly) {
      return myFontPreferences.getFontFamily();
    }
    if (myFaceName == null) {
      return getDelegate().getEditorFontName();
    }
    return myFaceName;
  }

  @Override
  public void setEditorFontName(String fontName) {
    if (Objects.equals(fontName, myFaceName)) return;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font name overridden for " + myEditor, new Throwable());
    }
    myFontPreferencesAreSetExplicitly = false;
    myFaceName = fontName;
    reinitFontsAndSettings();
  }

  @Override
  public @NotNull Font getFont(EditorFontType key) {
    if (myFontsMap != null) {
      Font font = myFontsMap.get(key);
      if (font != null) return font;
    }
    return getDelegate().getFont(key);
  }


  @Override
  public void setDelegate(@NotNull EditorColorsScheme delegate) {
    super.setDelegate(delegate);
    float globalFontSize = getDelegate().getEditorFontSize2D();
    myMaxFontSize = Math.max(EditorFontsConstants.getMaxEditorFontSize(), globalFontSize);
    reinitFonts();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    setConsoleFontSize((float)fontSize);
  }

  @Override
  public void setConsoleFontSize(float fontSize) {
    if (fontSize == super.getConsoleFontSize2D()) {
      myConsoleFontSize = FONT_SIZE_TO_IGNORE;
    }
    else {
      myConsoleFontSize = fontSize;
    }

    reinitFontsAndSettings();
  }

  @Override
  public int getConsoleFontSize() {
    return (int)(getConsoleFontSize2D() + 0.5);
  }

  @Override
  public float getConsoleFontSize2D() {
    return myConsoleFontSize == FONT_SIZE_TO_IGNORE ? super.getConsoleFontSize2D() : myConsoleFontSize;
  }

  @Override
  public float getLineSpacing() {
    return myLineSpacing == null ? super.getLineSpacing() : myLineSpacing;
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    float oldLineSpacing = getLineSpacing();
    float newLineSpacing = EditorFontsConstants.checkAndFixEditorLineSpacing(lineSpacing);
    myLineSpacing = newLineSpacing;
    if (oldLineSpacing != newLineSpacing) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public boolean isUseLigatures() {
    return myUseLigatures == null ? super.isUseLigatures() : myUseLigatures;
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    myUseLigatures = useLigatures;
    reinitFontsAndSettings();
  }

  @Override
  public @Nullable Object clone() {
    return null;
  }

  /**
   * Option to change font size by scroll for all editors (IDEA-158642)
   */
  void resetEditorFontSize() {
    myFontSize = FONT_SIZE_TO_IGNORE;
    reinitFonts();
  }

  /**
   * [UI] IDEA-314903 Update global editor color scheme in case of delegation
   * <p>
   * MyColorSchemeDelegate.updateGlobalScheme would not update the scheme
   * if it's not the default one (as denoted by myCustomGlobalScheme == null).
   * However, this custom scheme could actually be a scheme delegating to
   * the default global scheme, and in this case it must be updated, otherwise
   * it isn't switched properly when the global scheme is changed, which
   * is why consoles wouldn't update properly on a theme change.
   * <p>
   * Fix by going through the delegation chain until either a non-delegating
   * custom scheme is found (which should not be updated) or a scheme
   * delegating to the global default is found, which is then updated as usual
   */
  void updateGlobalScheme() {
    for (
      EditorColorsScheme scheme = this;
      scheme instanceof DelegateColorScheme delegateColorScheme;
      scheme = delegateColorScheme.getDelegate()
    ) {
      if (scheme instanceof EditorColorSchemeDelegate myColorSchemeDelegate) {
        myColorSchemeDelegate.updateDelegate();
      }
    }
  }

  private void reinitFontsAndSettings() {
    reinitFonts();
    myEditor.reinitSettings();
  }

  private void reinitFonts() {
    EditorColorsScheme delegate = getDelegate();
    String editorFontName = getEditorFontName();
    float editorFontSize = getEditorFontSize2D();
    if (!myFontPreferencesAreSetExplicitly) {
      updatePreferences(myFontPreferences, editorFontName, editorFontSize, myUseLigatures, delegate.getFontPreferences());
    }
    String consoleFontName = getConsoleFontName();
    float consoleFontSize = getConsoleFontSize2D();
    updatePreferences(myConsoleFontPreferences, consoleFontName, consoleFontSize, myUseLigatures, delegate.getConsoleFontPreferences());

    myFontsMap = new EnumMap<>(EditorFontType.class);
    setFont(EditorFontType.PLAIN, editorFontName, Font.PLAIN, editorFontSize, myFontPreferences);
    setFont(EditorFontType.BOLD, editorFontName, Font.BOLD, editorFontSize, myFontPreferences);
    setFont(EditorFontType.ITALIC, editorFontName, Font.ITALIC, editorFontSize, myFontPreferences);
    setFont(EditorFontType.BOLD_ITALIC, editorFontName, Font.BOLD | Font.ITALIC, editorFontSize, myFontPreferences);
    setFont(EditorFontType.CONSOLE_PLAIN, consoleFontName, Font.PLAIN, consoleFontSize, myConsoleFontPreferences);
    setFont(EditorFontType.CONSOLE_BOLD, consoleFontName, Font.BOLD, consoleFontSize, myConsoleFontPreferences);
    setFont(EditorFontType.CONSOLE_ITALIC, consoleFontName, Font.ITALIC, consoleFontSize, myConsoleFontPreferences);
    setFont(EditorFontType.CONSOLE_BOLD_ITALIC, consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize, myConsoleFontPreferences);
  }

  private void setFont(
    @NotNull EditorFontType fontType,
    @NotNull String familyName,
    int style,
    float fontSize,
    @NotNull FontPreferences fontPreferences
  ) {
    Font baseFont = FontFamilyService.getFont(
      familyName,
      fontPreferences.getRegularSubFamily(),
      fontPreferences.getBoldSubFamily(),
      style,
      fontSize
    );
    Font fontWithLigatures = EditorFontCacheImpl.deriveFontWithLigatures(
      baseFont,
      myUseLigatures != null
      ? myUseLigatures
      : fontPreferences.useLigatures()
    );
    myFontsMap.put(fontType, fontWithLigatures);
  }

  private void updateDelegate() {
    var delegate = myCustomGlobalScheme == null
                   ? EditorColorsManager.getInstance().getGlobalScheme()
                   : myCustomGlobalScheme;
    setDelegate(delegate);
  }

  private static void updatePreferences(
    @NotNull FontPreferencesImpl preferences,
    @NotNull String fontName,
    float fontSize,
    @Nullable Boolean useLigatures,
    @NotNull FontPreferences delegatePreferences
  ) {
    preferences.clear();
    preferences.register(fontName, fontSize);
    List<String> families = delegatePreferences.getRealFontFamilies();
    //skip delegate's primary font
    for (int i = 1; i < families.size(); i++) {
      String font = families.get(i);
      preferences.register(font, fontSize);
    }
    preferences.setUseLigatures(useLigatures != null ? useLigatures : delegatePreferences.useLigatures());
    preferences.setRegularSubFamily(delegatePreferences.getRegularSubFamily());
    preferences.setBoldSubFamily(delegatePreferences.getBoldSubFamily());
  }
}
