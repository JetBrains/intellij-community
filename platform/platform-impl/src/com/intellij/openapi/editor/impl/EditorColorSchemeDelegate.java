// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.colors.impl.EditorFonts;
import com.intellij.openapi.editor.colors.impl.FontPreferencesSnapshot;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;

/**
 * The colors scheme of one editor. It delegates to the global scheme, or to a custom scheme, and keeps the settings that the editor
 * changes for itself.
 * <p>
 * The state of the editor is one {@link EditorColorSchemeSnapshot}, so a reader on any thread sees one consistent version.
 * Its history:
 * <ul>
 *   <li>[CR-IC-7467] separated the FontPreferences interface and implementation, added a flag to inherit console font preferences from
 *   editor ones, and moved line spacing to FontPreferences</li>
 *   <li>IDEA-129631 added fractional font sizes</li>
 *   <li>IDEA-304086 resets the font size of the editor, so the editor font follows the settings after Presentation mode</li>
 *   <li>the line spacing can be set for each editor</li>
 *   <li>IDEA-240654 added ligatures for each editor</li>
 *   <li>IDEA-268567 and IDEA-294744 added an override of the ligatures setting without a replacement of the whole FontPreferences</li>
 * </ul>
 */
final class EditorColorSchemeDelegate extends DelegateColorScheme {
  private static final Logger LOG = Logger.getInstance(EditorColorSchemeDelegate.class);
  private static final AtomicReferenceFieldUpdater<EditorColorSchemeDelegate, EditorColorSchemeSnapshot> SNAPSHOT_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(EditorColorSchemeDelegate.class, EditorColorSchemeSnapshot.class, "mySnapshot");

  private final EditorImpl myEditor;
  private final EditorColorsScheme myCustomGlobalScheme; // RUBY-6775: Force Editor Text fields to use default color scheme with white background
  private volatile @NotNull EditorColorSchemeSnapshot mySnapshot;

  EditorColorSchemeDelegate(@NotNull EditorImpl editor, @Nullable EditorColorsScheme globalScheme) {
    super(globalScheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : globalScheme);
    myEditor = editor;
    myCustomGlobalScheme = globalScheme;
    mySnapshot = EditorColorSchemeSnapshot.initial();
    updateGlobalScheme();
  }

  @Override
  public TextAttributes getAttributes(TextAttributesKey key) {
    Map<TextAttributesKey, TextAttributes> ownAttributes = mySnapshot.ownAttributes();
    if (ownAttributes.containsKey(key)) {
      return ownAttributes.get(key);
    }
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
    } else {
      updateSnapshot(snapshot -> snapshot.withOwnAttributes(key, attributes));
    }
  }

  @Override
  public @Nullable Color getColor(ColorKey key) {
    Map<ColorKey, Color> ownColors = mySnapshot.ownColors();
    if (ownColors.containsKey(key)) {
      return ownColors.get(key);
    }
    return getDelegate().getColor(key);
  }

  @Override
  public void setColor(ColorKey key, Color color) {
    boolean changed = color == AbstractColorsScheme.INHERITED_COLOR_MARKER
                      ? updateSnapshot(snapshot -> snapshot.withoutOwnColor(key))
                      : updateSnapshot(snapshot -> snapshot.withOwnColor(key, color));
    if (changed) {
      // These two are here because those attributes are cached
      // and I do not want the clients to call editor's reinit settings in this case.
      myEditor.getCaretModel().reinitSettings();
      myEditor.getSelectionModel().reinitSettings();
    }
  }

  @Override
  public int getEditorFontSize() {
    return (int)(getEditorFontSize2D() + 0.5);
  }

  @Override
  public float getEditorFontSize2D() {
    return getEditorFontSize2D(mySnapshot);
  }

  @Override
  public void setEditorFontSize(int fontSize) {
    setEditorFontSize((float)fontSize);
  }

  @Override
  public void setEditorFontSize(float fontSize) {
    // IDEA-315284 Adjust min and max font size in EditorImpl.MyColorSchemeDelegate
    float originalSize = UISettingsUtils.getInstance().scaleFontSize(getDelegate().getEditorFontSize2D());
    EditorColorSchemeSnapshot current = mySnapshot;
    float minSize = Math.min(EditorImpl.MIN_FONT_SIZE, originalSize);
    if (fontSize < minSize) {
      fontSize = minSize;
    }
    float maxSize = Math.max(current.maxFontSize(), originalSize);
    if (fontSize > maxSize) {
      fontSize = maxSize;
    }
    Float currentFontSize = current.fontSize();
    if (currentFontSize != null && currentFontSize == fontSize) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font size overridden for " + myEditor, new Throwable());
    }
    Float newFontSize = fontSize == originalSize ? null : fontSize;
    boolean changed = updateSnapshot(snapshot -> withFonts(
      snapshot
        .withFontPreferencesSetExplicitly(false)
        .withFontSize(newFontSize)
    ));
    if (changed) {
      myEditor.reinitSettings(true, false);
    }
  }

  @Override
  public @NotNull FontPreferences getFontPreferences() {
    EditorColorSchemeSnapshot snapshot = mySnapshot;
    FontPreferences fontPreferences = snapshot.fontPreferences();
    if (snapshot.fontPreferencesAreSetExplicitly() || !fontPreferences.getEffectiveFontFamilies().isEmpty()) {
      return fontPreferences;
    }
    return getDelegate().getFontPreferences();
  }

  @Override
  public void setFontPreferences(@NotNull FontPreferences preferences) {
    EditorColorSchemeSnapshot current = mySnapshot;
    if (current.fontPreferencesAreSetExplicitly() && preferences.getSnapshot() == current.fontPreferences()) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font preferences overridden for " + myEditor, new Throwable());
    }
    boolean changed = updateSnapshot(snapshot -> withFonts(
      snapshot
        .withFontPreferencesSetExplicitly(true)
        .withFaceName(null)
        .withFontSize(null)
        .withFontPreferences(snapshot.fontPreferences().withChanges(preferences::copyTo))
    ));
    if (changed) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public @NotNull FontPreferences getConsoleFontPreferences() {
    FontPreferences consoleFontPreferences = mySnapshot.consoleFontPreferences();
    if (consoleFontPreferences.getEffectiveFontFamilies().isEmpty()) {
      return getDelegate().getConsoleFontPreferences();
    }
    return consoleFontPreferences;
  }

  @Override
  public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
    if (preferences.getSnapshot() == mySnapshot.consoleFontPreferences()) {
      return;
    }
    boolean changed = updateSnapshot(snapshot -> withFonts(
      snapshot
        .withConsoleFontPreferences(
          snapshot.consoleFontPreferences().withChanges(preferences::copyTo)
        )
    ));
    if (changed) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public String getEditorFontName() {
    return getEditorFontName(mySnapshot);
  }

  @Override
  public void setEditorFontName(String fontName) {
    if (Objects.equals(fontName, mySnapshot.faceName())) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Font name overridden for " + myEditor, new Throwable());
    }
    boolean changed = updateSnapshot(snapshot -> withFonts(
      snapshot
        .withFontPreferencesSetExplicitly(false)
        .withFaceName(fontName)
    ));
    if (changed) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public @NotNull Font getFont(EditorFontType key) {
    EditorFonts fonts = mySnapshot.fonts();
    if (key != null && !fonts.isEmpty()) {
      return fonts.get(key);
    }
    return getDelegate().getFont(key);
  }

  @Override
  public void setDelegate(@NotNull EditorColorsScheme delegate) {
    super.setDelegate(delegate);
    float maxFontSize = Math.max(EditorFontsConstants.getMaxEditorFontSize(), getDelegate().getEditorFontSize2D());
    updateSnapshot(snapshot -> withFonts(
      snapshot.withMaxFontSize(maxFontSize)
    ));
    myEditor.reinitStickyLines();
  }

  @Override
  public void setConsoleFontSize(int fontSize) {
    setConsoleFontSize((float)fontSize);
  }

  @Override
  public void setConsoleFontSize(float fontSize) {
    Float newConsoleFontSize = fontSize == super.getConsoleFontSize2D() ? null : fontSize;
    boolean changed = updateSnapshot(snapshot -> withFonts(
      snapshot.withConsoleFontSize(newConsoleFontSize)
    ));
    if (changed) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public int getConsoleFontSize() {
    return (int)(getConsoleFontSize2D() + 0.5);
  }

  @Override
  public float getConsoleFontSize2D() {
    return getConsoleFontSize2D(mySnapshot);
  }

  @Override
  public float getLineSpacing() {
    Float lineSpacing = mySnapshot.lineSpacing();
    return lineSpacing == null ? super.getLineSpacing() : lineSpacing;
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    float oldLineSpacing = getLineSpacing();
    float newLineSpacing = EditorFontsConstants.checkAndFixEditorLineSpacing(lineSpacing);
    updateSnapshot(snapshot -> snapshot.withLineSpacing(newLineSpacing));
    if (oldLineSpacing != newLineSpacing) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public boolean isUseLigatures() {
    Boolean useLigatures = mySnapshot.useLigatures();
    return useLigatures == null ? super.isUseLigatures() : useLigatures;
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    if (updateSnapshot(snapshot -> withFonts(snapshot.withUseLigatures(useLigatures)))) {
      myEditor.reinitSettings();
    }
  }

  /// @noinspection MethodDoesntCallSuperMethod legacy
  @Override
  public @Nullable Object clone() {
    return null;
  }

  /**
   * Option to change font size by scroll for all editors (IDEA-158642)
   */
  void resetEditorFontSize() {
    updateSnapshot(snapshot -> withFonts(snapshot.withFontSize(null)));
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

  /**
   * Replaces the snapshot with the result of the operation.
   * The operation can run more than once, so it must have no side effects.
   *
   * @return true when the snapshot changed. A setter runs its side effects only then.
   */
  private boolean updateSnapshot(@NotNull UnaryOperator<EditorColorSchemeSnapshot> operation) {
    while (true) {
      EditorColorSchemeSnapshot prev = mySnapshot;
      EditorColorSchemeSnapshot next = operation.apply(prev);
      if (next == prev) {
        return false;
      }
      if (SNAPSHOT_UPDATER.compareAndSet(this, prev, next)) {
        return true;
      }
    }
  }

  private float getEditorFontSize2D(@NotNull EditorColorSchemeSnapshot snapshot) {
    if (snapshot.fontPreferencesAreSetExplicitly()) {
      FontPreferences fontPreferences = snapshot.fontPreferences();
      return fontPreferences.getSize2D(fontPreferences.getFontFamily());
    }
    Float fontSize = snapshot.fontSize();
    if (fontSize == null) {
      // IDEA-311926 Specify UISettings for UISettingsUtils when necessary
      return UISettingsUtils.getInstance().scaleFontSize(getDelegate().getEditorFontSize2D());
    }
    return fontSize;
  }

  private String getEditorFontName(@NotNull EditorColorSchemeSnapshot snapshot) {
    if (snapshot.fontPreferencesAreSetExplicitly()) {
      return snapshot.fontPreferences().getFontFamily();
    }
    String faceName = snapshot.faceName();
    if (faceName == null) {
      return getDelegate().getEditorFontName();
    }
    return faceName;
  }

  private float getConsoleFontSize2D(@NotNull EditorColorSchemeSnapshot snapshot) {
    Float consoleFontSize = snapshot.consoleFontSize();
    return consoleFontSize == null ? super.getConsoleFontSize2D() : consoleFontSize;
  }

  /**
   * Returns {@code snapshot} with the font preferences and the fonts that its settings and the delegate scheme give.
   * It keeps the fonts of {@code snapshot} when their settings are the same.
   * It has no side effects, so {@link #updateSnapshot} can run it more than once.
   */
  private @NotNull EditorColorSchemeSnapshot withFonts(@NotNull EditorColorSchemeSnapshot snapshot) {
    EditorColorsScheme delegate = getDelegate();
    Boolean useLigatures = snapshot.useLigatures();
    String editorFontName = getEditorFontName(snapshot);
    float editorFontSize = getEditorFontSize2D(snapshot);
    FontPreferencesSnapshot fontPreferences = snapshot.fontPreferences();
    if (!snapshot.fontPreferencesAreSetExplicitly()) {
      fontPreferences = updatePreferences(fontPreferences, editorFontName, editorFontSize, useLigatures, delegate.getFontPreferences());
    }
    String consoleFontName = delegate.getConsoleFontName();
    float consoleFontSize = getConsoleFontSize2D(snapshot);
    FontPreferencesSnapshot consoleFontPreferences = updatePreferences(
      snapshot.consoleFontPreferences(), consoleFontName, consoleFontSize, useLigatures, delegate.getConsoleFontPreferences()
    );
    EditorFonts fonts = snapshot.fonts().with(
      editorFontName, editorFontSize, fontPreferences, consoleFontName, consoleFontSize, consoleFontPreferences, useLigatures
    );
    return snapshot
      .withFontPreferences(fontPreferences)
      .withConsoleFontPreferences(consoleFontPreferences)
      .withFonts(fonts);
  }

  private void updateDelegate() {
    var delegate = myCustomGlobalScheme == null
                   ? EditorColorsManager.getInstance().getGlobalScheme()
                   : myCustomGlobalScheme;
    setDelegate(delegate);
  }

  /**
   * Returns {@code preferences} with the font, the font size, and the other settings of {@code delegatePreferences}.
   */
  private static @NotNull FontPreferencesSnapshot updatePreferences(
    @NotNull FontPreferencesSnapshot preferences,
    @NotNull String fontName,
    float fontSize,
    @Nullable Boolean useLigatures,
    @NotNull FontPreferences delegatePreferences
  ) {
    return preferences.withChanges(draft -> {
      draft.clear();
      draft.register(fontName, fontSize);
      List<String> families = delegatePreferences.getRealFontFamilies();
      //skip delegate's primary font
      for (int i = 1; i < families.size(); i++) {
        String font = families.get(i);
        draft.register(font, fontSize);
      }
      draft.setUseLigatures(useLigatures != null ? useLigatures : delegatePreferences.useLigatures());
      draft.setCharacterVariants(delegatePreferences.getCharacterVariants());
      draft.setRegularSubFamily(delegatePreferences.getRegularSubFamily());
      draft.setBoldSubFamily(delegatePreferences.getBoldSubFamily());
    });
  }
}
