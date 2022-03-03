// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.ide.ui.UINumericRange;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@State(name = "EditorSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public class EditorSettingsExternalizable implements PersistentStateComponent<EditorSettingsExternalizable.OptionSet> {
  @NonNls
  public static final String PROP_VIRTUAL_SPACE = "VirtualSpace";
  @NonNls
  public static final String PROP_BREADCRUMBS_PER_LANGUAGE = "BreadcrumbsPerLanguage";

  @NonNls
  public static final String PROP_DOC_COMMENT_RENDERING = "DocCommentRendering";

  public static final UINumericRange BLINKING_RANGE = new UINumericRange(500, 10, 1500);
  public static final UINumericRange TOOLTIPS_DELAY_RANGE = new UINumericRange(500, 1, 5000);

  private static final String SOFT_WRAP_FILE_MASKS_ENABLED_DEFAULT = "*";
  @NonNls private static final String SOFT_WRAP_FILE_MASKS_DISABLED_DEFAULT = "*.md; *.txt; *.rst; *.adoc";

  //Q: make it interface?
  public static final class OptionSet {
    public String LINE_SEPARATOR;
    public String USE_SOFT_WRAPS;
    public String SOFT_WRAP_FILE_MASKS;
    public boolean USE_CUSTOM_SOFT_WRAP_INDENT = true;
    public int CUSTOM_SOFT_WRAP_INDENT = 0;
    public boolean IS_VIRTUAL_SPACE = false;
    public boolean IS_CARET_INSIDE_TABS;
    @NonNls public String STRIP_TRAILING_SPACES = STRIP_TRAILING_SPACES_CHANGED;
    public boolean IS_ENSURE_NEWLINE_AT_EOF = false;
    public boolean REMOVE_TRAILING_BLANK_LINES = false;
    public boolean SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = true;
    public boolean SHOW_INSPECTION_WIDGET = true;
    public int TOOLTIPS_DELAY_MS = TOOLTIPS_DELAY_RANGE.initial;
    public boolean SHOW_INTENTION_BULB = true;
    public boolean IS_CARET_BLINKING = true;
    public int CARET_BLINKING_PERIOD = BLINKING_RANGE.initial;
    public boolean IS_RIGHT_MARGIN_SHOWN = true;
    public boolean ARE_LINE_NUMBERS_SHOWN = true;
    public boolean ARE_GUTTER_ICONS_SHOWN = true;
    public boolean IS_FOLDING_OUTLINE_SHOWN = true;
    public boolean SHOW_BREADCRUMBS_ABOVE = false;
    public boolean SHOW_BREADCRUMBS = true;
    public boolean ENABLE_RENDERED_DOC = false;
    public boolean SHOW_INTENTION_PREVIEW = false;
    public boolean USE_EDITOR_FONT_IN_INLAYS = false;

    public boolean SMART_HOME = true;

    public boolean IS_BLOCK_CURSOR = false;
    public boolean IS_WHITESPACES_SHOWN = false;
    public boolean IS_LEADING_WHITESPACES_SHOWN = true;
    public boolean IS_INNER_WHITESPACES_SHOWN = true;
    public boolean IS_TRAILING_WHITESPACES_SHOWN = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_ALL_SOFTWRAPS_SHOWN = false;
    public boolean IS_INDENT_GUIDES_SHOWN = true;
    public boolean IS_FOCUS_MODE = false;
    public boolean IS_ANIMATED_SCROLLING = true;
    public boolean IS_CAMEL_WORDS = false;
    public boolean ADDITIONAL_PAGE_AT_BOTTOM = false;

    public boolean IS_DND_ENABLED = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_WHEEL_FONTCHANGE_ENABLED = false;
    public boolean IS_WHEEL_FONTCHANGE_PERSISTENT = false;
    public boolean IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = true;

    public boolean RENAME_VARIABLES_INPLACE = true;
    public boolean PRESELECT_RENAME = true;
    public boolean SHOW_INLINE_DIALOG = true;

    public boolean REFRAIN_FROM_SCROLLING = false;

    public boolean ADD_CARETS_ON_DOUBLE_CTRL = true;

    public BidiTextDirection BIDI_TEXT_DIRECTION = BidiTextDirection.CONTENT_BASED;

    public boolean SHOW_PARAMETER_NAME_HINTS = true;

    public boolean KEEP_TRAILING_SPACE_ON_CARET_LINE = true;

    private final Map<String, Boolean> mapLanguageBreadcrumbs = new HashMap<>();

    public Map<String, Boolean> getLanguageBreadcrumbsMap() {
      return mapLanguageBreadcrumbs;
    }

    @SuppressWarnings("unused")
    public void setLanguageBreadcrumbsMap(Map<String, Boolean> map) {
      if (this.mapLanguageBreadcrumbs != map) {
        this.mapLanguageBreadcrumbs.clear();
        this.mapLanguageBreadcrumbs.putAll(map);
      }
    }
  }

  @State(
    name = "OsSpecificEditorSettings",
    storages = @Storage(value = "editor.os-specific.xml", roamingType = RoamingType.PER_OS),
    category = SettingsCategory.CODE
  )
  public static final class OsSpecificState implements PersistentStateComponent<OsSpecificState> {
    public CaretStopOptions CARET_STOP_OPTIONS = new CaretStopOptions();

    @Override
    public OsSpecificState getState() {
      return this;
    }

    @Override
    public void loadState(@NotNull OsSpecificState state) {
      CARET_STOP_OPTIONS = state.CARET_STOP_OPTIONS;
    }
  }

  private static final String COMPOSITE_PROPERTY_SEPARATOR = ":";

  @NotNull private final OsSpecificState myOsSpecificState;

  private final Set<SoftWrapAppliancePlaces> myPlacesToUseSoftWraps = EnumSet.noneOf(SoftWrapAppliancePlaces.class);
  private OptionSet myOptions = new OptionSet();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Map<String, Boolean> myDefaultBreadcrumbVisibility = new HashMap<>();

  private int myBlockIndent;

  @NonNls public static final String STRIP_TRAILING_SPACES_NONE = "None";
  @NonNls public static final String STRIP_TRAILING_SPACES_CHANGED = "Changed";
  @NonNls public static final String STRIP_TRAILING_SPACES_WHOLE = "Whole";

  @MagicConstant(stringValues = {STRIP_TRAILING_SPACES_NONE, STRIP_TRAILING_SPACES_CHANGED, STRIP_TRAILING_SPACES_WHOLE})
  public @interface StripTrailingSpaces {}

  public EditorSettingsExternalizable() {
    this(ApplicationManager.getApplication().getService(OsSpecificState.class));
  }

  @NonInjectable
  public EditorSettingsExternalizable(@NotNull OsSpecificState state) {
    myOsSpecificState = state;
  }

  public static EditorSettingsExternalizable getInstance() {
    return ApplicationManager.getApplication().getService(EditorSettingsExternalizable.class);
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable disposable) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
    Disposer.register(disposable, () -> myPropertyChangeSupport.removePropertyChangeListener(listener));
  }

  @NotNull
  @Override
  public OptionSet getState() {
    return myOptions;
  }

  @Override
  public void loadState(@NotNull OptionSet state) {
    myOptions = state;
    parseRawSoftWraps();
  }

  private void parseRawSoftWraps() {
    myPlacesToUseSoftWraps.clear();

    if (StringUtil.isEmpty(myOptions.USE_SOFT_WRAPS)) {
      return;
    }

    String[] placeNames = myOptions.USE_SOFT_WRAPS.split(COMPOSITE_PROPERTY_SEPARATOR);
    for (String placeName : placeNames) {
      try {
        SoftWrapAppliancePlaces place = SoftWrapAppliancePlaces.valueOf(placeName);
        myPlacesToUseSoftWraps.add(place);
      }
      catch (IllegalArgumentException e) {
        // Ignore bad value
      }
    }

    // There is a possible case that there were invalid/old format values. We want to replace them by up-to-date data.
    storeRawSoftWraps();
  }

  private void storeRawSoftWraps() {
    StringBuilder buffer = new StringBuilder();
    for (SoftWrapAppliancePlaces placeToStore : myPlacesToUseSoftWraps) {
      buffer.append(placeToStore).append(COMPOSITE_PROPERTY_SEPARATOR);
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 1);
    }
    myOptions.USE_SOFT_WRAPS = buffer.toString();
  }

  public OptionSet getOptions() {
    return myOptions;
  }

  public boolean isRightMarginShown() {
    return myOptions.IS_RIGHT_MARGIN_SHOWN;
  }

  public void setRightMarginShown(boolean val) {
    myOptions.IS_RIGHT_MARGIN_SHOWN = val;
  }

  public boolean isLineNumbersShown() {
    return myOptions.ARE_LINE_NUMBERS_SHOWN;
  }

  public void setLineNumbersShown(boolean val) {
    myOptions.ARE_LINE_NUMBERS_SHOWN = val;
  }

  public boolean areGutterIconsShown() {
    return myOptions.ARE_GUTTER_ICONS_SHOWN;
  }

  public void setGutterIconsShown(boolean val) {
    myOptions.ARE_GUTTER_ICONS_SHOWN = val;
  }

  public boolean isFoldingOutlineShown() {
    return myOptions.IS_FOLDING_OUTLINE_SHOWN;
  }

  public void setFoldingOutlineShown(boolean val) {
    myOptions.IS_FOLDING_OUTLINE_SHOWN = val;
  }

  /**
   * @return {@code true} if breadcrumbs should be shown above the editor, {@code false} otherwise
   */
  public boolean isBreadcrumbsAbove() {
    return myOptions.SHOW_BREADCRUMBS_ABOVE;
  }

  /**
   * @param value {@code true} if breadcrumbs should be shown above the editor, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsAbove(boolean value) {
    if (myOptions.SHOW_BREADCRUMBS_ABOVE == value) return false;
    myOptions.SHOW_BREADCRUMBS_ABOVE = value;
    return true;
  }

  /**
   * @return {@code true} if breadcrumbs should be shown, {@code false} otherwise
   */
  public boolean isBreadcrumbsShown() {
    return myOptions.SHOW_BREADCRUMBS;
  }

  /**
   * @param value {@code true} if breadcrumbs should be shown, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsShown(boolean value) {
    if (myOptions.SHOW_BREADCRUMBS == value) return false;
    myOptions.SHOW_BREADCRUMBS = value;
    return true;
  }

  /**
   * @param languageID the language identifier to configure
   * @return {@code true} if breadcrumbs should be shown for the specified language, {@code false} otherwise
   */
  public boolean isBreadcrumbsShownFor(String languageID) {
    Boolean visible = myOptions.mapLanguageBreadcrumbs.get(languageID);
    if (visible == null) {
      Boolean defaultVisible = myDefaultBreadcrumbVisibility.get(languageID);
      if (defaultVisible == null) {
        for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensionList()) {
          for (Language language : provider.getLanguages()) {
            myDefaultBreadcrumbVisibility.put(language.getID(), provider.isShownByDefault());
          }
        }
        defaultVisible = myDefaultBreadcrumbVisibility.get(languageID);
      }
      return defaultVisible == null || defaultVisible;
    }
    return visible;
  }

  public void resetDefaultBreadcrumbVisibility() {
    myDefaultBreadcrumbVisibility.clear();
  }

  public boolean hasBreadcrumbSettings(String languageID) {
    return myOptions.mapLanguageBreadcrumbs.containsKey(languageID);
  }

  /**
   * @param languageID the language identifier to configure
   * @param value      {@code true} if breadcrumbs should be shown for the specified language, {@code false} otherwise
   * @return {@code true} if an option was modified, {@code false} otherwise
   */
  public boolean setBreadcrumbsShownFor(String languageID, boolean value) {
    Boolean visible = myOptions.mapLanguageBreadcrumbs.put(languageID, value);
    boolean newValue = (visible == null || visible) != value;
    if (newValue) {
      myPropertyChangeSupport.firePropertyChange(PROP_BREADCRUMBS_PER_LANGUAGE, visible, (Boolean)value);
    }
    return newValue;
  }

  public boolean isDocCommentRenderingEnabled() {
    return myOptions.ENABLE_RENDERED_DOC;
  }

  public void setDocCommentRenderingEnabled(boolean value) {
    boolean oldValue = myOptions.ENABLE_RENDERED_DOC;
    myOptions.ENABLE_RENDERED_DOC = value;
    if (oldValue != value) {
      myPropertyChangeSupport.firePropertyChange(PROP_DOC_COMMENT_RENDERING, oldValue, value);
    }
  }

  public boolean isBlockCursor() {
    return myOptions.IS_BLOCK_CURSOR;
  }

  public void setBlockCursor(boolean val) {
    myOptions.IS_BLOCK_CURSOR = val;
  }

  public boolean isCaretRowShown() {
    return true;
  }

  public int getBlockIndent() {
    return myBlockIndent;
  }

  public void setBlockIndent(int blockIndent) {
    myBlockIndent = blockIndent;
  }

  public boolean isSmartHome() {
    return myOptions.SMART_HOME;
  }

  public void setSmartHome(boolean val) {
    myOptions.SMART_HOME = val;
  }

  public boolean isUseSoftWraps() {
    return isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR);
  }

  public boolean isUseSoftWraps(@NotNull SoftWrapAppliancePlaces place) {
    return myPlacesToUseSoftWraps.contains(place);
  }

  public void setUseSoftWraps(boolean use) {
    setUseSoftWraps(use, SoftWrapAppliancePlaces.MAIN_EDITOR);
  }

  public void setUseSoftWraps(boolean use, @NotNull SoftWrapAppliancePlaces place) {
    boolean update = use ^ myPlacesToUseSoftWraps.contains(place);
    if (!update) {
      return;
    }

    if (use) {
      myPlacesToUseSoftWraps.add(place);
    }
    else {
      myPlacesToUseSoftWraps.remove(place);
    }
    storeRawSoftWraps();
    if (place == SoftWrapAppliancePlaces.MAIN_EDITOR) {
      setSoftWrapFileMasks(getSoftWrapFileMasks());
    }
  }

  public boolean isUseCustomSoftWrapIndent() {
    return myOptions.USE_CUSTOM_SOFT_WRAP_INDENT;
  }

  public void setUseCustomSoftWrapIndent(boolean use) {
    myOptions.USE_CUSTOM_SOFT_WRAP_INDENT = use;
  }

  public int getCustomSoftWrapIndent() {
    return myOptions.CUSTOM_SOFT_WRAP_INDENT;
  }

  public void setCustomSoftWrapIndent(int indent) {
    myOptions.CUSTOM_SOFT_WRAP_INDENT = indent;
  }

  public boolean isVirtualSpace() {
    return myOptions.IS_VIRTUAL_SPACE;
  }

  public void setVirtualSpace(boolean val) {
    boolean oldValue = myOptions.IS_VIRTUAL_SPACE;
    myOptions.IS_VIRTUAL_SPACE = val;
    myPropertyChangeSupport.firePropertyChange(PROP_VIRTUAL_SPACE, oldValue, val);
  }

  public boolean isCaretInsideTabs() {
    return myOptions.IS_CARET_INSIDE_TABS;
  }

  public void setCaretInsideTabs(boolean val) {
    myOptions.IS_CARET_INSIDE_TABS = val;
  }

  public boolean isBlinkCaret() {
    return myOptions.IS_CARET_BLINKING;
  }

  public void setBlinkCaret(boolean blinkCaret) {
    myOptions.IS_CARET_BLINKING = blinkCaret;
  }

  public int getBlinkPeriod() {
    return BLINKING_RANGE.fit(myOptions.CARET_BLINKING_PERIOD);
  }

  public void setBlinkPeriod(int blinkInterval) {
    myOptions.CARET_BLINKING_PERIOD = BLINKING_RANGE.fit(blinkInterval);
  }


  public boolean isEnsureNewLineAtEOF() {
    return myOptions.IS_ENSURE_NEWLINE_AT_EOF;
  }

  public void setEnsureNewLineAtEOF(boolean ensure) {
    myOptions.IS_ENSURE_NEWLINE_AT_EOF = ensure;
  }

  public boolean isRemoveTrailingBlankLines() {
    return myOptions.REMOVE_TRAILING_BLANK_LINES;
  }

  public void setRemoveTrailingBlankLines(boolean remove) {
    myOptions.REMOVE_TRAILING_BLANK_LINES = remove;
  }

  @StripTrailingSpaces
  public String getStripTrailingSpaces() {
    return myOptions.STRIP_TRAILING_SPACES;
  } // TODO: move to CodeEditorManager or something else

  public void setStripTrailingSpaces(@StripTrailingSpaces String stripTrailingSpaces) {
    myOptions.STRIP_TRAILING_SPACES = stripTrailingSpaces;
  }

  public boolean isShowQuickDocOnMouseOverElement() {
    return myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT && !AccessibilityUtils.isScreenReaderDetected();
  }

  public void setShowQuickDocOnMouseOverElement(boolean show) {
    myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = show;
  }


  public boolean isShowInspectionWidget() {
    return myOptions.SHOW_INSPECTION_WIDGET;
  }

  public void setShowInspectionWidget(boolean show) {
    myOptions.SHOW_INSPECTION_WIDGET = show;
  }

  /**
   * @deprecated Use {@link #getTooltipsDelay()} instead
   */
  @Deprecated
  public int getQuickDocOnMouseOverElementDelayMillis() {
    return getTooltipsDelay();
  }

  public int getTooltipsDelay() {
    return TOOLTIPS_DELAY_RANGE.fit(myOptions.TOOLTIPS_DELAY_MS);
  }

  public void setTooltipsDelay(int delay) {
    myOptions.TOOLTIPS_DELAY_MS = TOOLTIPS_DELAY_RANGE.fit(delay);
  }

  public boolean isShowIntentionBulb() {
    return myOptions.SHOW_INTENTION_BULB;
  }

  public void setShowIntentionBulb(boolean show) {
    myOptions.SHOW_INTENTION_BULB = show;
  }

  public boolean isRefrainFromScrolling() {
    return myOptions.REFRAIN_FROM_SCROLLING;
  }

  public void setRefrainFromScrolling(boolean b) {
    myOptions.REFRAIN_FROM_SCROLLING = b;
  }

  public boolean isShowNotificationAfterReformat() {
    return Registry.is("editor.show.notification.after.reformat");
  }

  public boolean isShowNotificationAfterOptimizeImports() {
    return Registry.is("editor.show.notification.after.optimize.imports");
  }

  public boolean isWhitespacesShown() {
    return myOptions.IS_WHITESPACES_SHOWN;
  }

  public void setWhitespacesShown(boolean val) {
    myOptions.IS_WHITESPACES_SHOWN = val;
  }

  public boolean isLeadingWhitespacesShown() {
    return myOptions.IS_LEADING_WHITESPACES_SHOWN;
  }

  public void setLeadingWhitespacesShown(boolean val) {
    myOptions.IS_LEADING_WHITESPACES_SHOWN = val;
  }

  public boolean isInnerWhitespacesShown() {
    return myOptions.IS_INNER_WHITESPACES_SHOWN;
  }

  public void setInnerWhitespacesShown(boolean val) {
    myOptions.IS_INNER_WHITESPACES_SHOWN = val;
  }

  public boolean isTrailingWhitespacesShown() {
    return myOptions.IS_TRAILING_WHITESPACES_SHOWN;
  }

  public void setTrailingWhitespacesShown(boolean val) {
    myOptions.IS_TRAILING_WHITESPACES_SHOWN = val;
  }

  public boolean isAllSoftWrapsShown() {
    return myOptions.IS_ALL_SOFTWRAPS_SHOWN;
  }

  public void setAllSoftwrapsShown(boolean val) {
    myOptions.IS_ALL_SOFTWRAPS_SHOWN = val;
  }

  public boolean isIndentGuidesShown() {
    return myOptions.IS_INDENT_GUIDES_SHOWN;
  }

  public void setIndentGuidesShown(boolean val) {
    myOptions.IS_INDENT_GUIDES_SHOWN = val;
  }

  public boolean isFocusMode() {
    return myOptions.IS_FOCUS_MODE;
  }

  public void setFocusMode(boolean val) {
    myOptions.IS_FOCUS_MODE = val;
  }

  public boolean isSmoothScrolling() {
    return myOptions.IS_ANIMATED_SCROLLING;
  }

  public void setSmoothScrolling(boolean val){
    myOptions.IS_ANIMATED_SCROLLING = val;
  }

  public boolean isCamelWords() {
    return myOptions.IS_CAMEL_WORDS;
  }

  public void setCamelWords(boolean val) {
    myOptions.IS_CAMEL_WORDS = val;
  }

  public boolean isAdditionalPageAtBottom() {
    return myOptions.ADDITIONAL_PAGE_AT_BOTTOM;
  }

  public void setAdditionalPageAtBottom(boolean val) {
    myOptions.ADDITIONAL_PAGE_AT_BOTTOM = val;
  }

  public boolean isDndEnabled() {
    return myOptions.IS_DND_ENABLED;
  }

  public void setDndEnabled(boolean val) {
    myOptions.IS_DND_ENABLED = val;
  }

  public boolean isWheelFontChangeEnabled() {
    return myOptions.IS_WHEEL_FONTCHANGE_ENABLED;
  }

  public void setWheelFontChangeEnabled(boolean val) {
    myOptions.IS_WHEEL_FONTCHANGE_ENABLED = val;
  }

  public boolean isWheelFontChangePersistent() {
    return myOptions.IS_WHEEL_FONTCHANGE_PERSISTENT;
  }

  public void setWheelFontChangePersistent(boolean val) {
    myOptions.IS_WHEEL_FONTCHANGE_PERSISTENT = val;
  }

  public boolean isMouseClickSelectionHonorsCamelWords() {
    return myOptions.IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS;
  }

  public void setMouseClickSelectionHonorsCamelWords(boolean val) {
    myOptions.IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = val;
  }

  public boolean isVariableInplaceRenameEnabled() {
    return myOptions.RENAME_VARIABLES_INPLACE;
  }

  public void setVariableInplaceRenameEnabled(final boolean val) {
    myOptions.RENAME_VARIABLES_INPLACE = val;
  }

  public boolean isPreselectRename() {
    return myOptions.PRESELECT_RENAME;
  }

  public void setPreselectRename(final boolean val) {
    myOptions.PRESELECT_RENAME = val;
  }

  public boolean isShowInlineLocalDialog() {
    return myOptions.SHOW_INLINE_DIALOG;
  }

  public void setShowInlineLocalDialog(final boolean val) {
    myOptions.SHOW_INLINE_DIALOG = val;
  }

  public boolean addCaretsOnDoubleCtrl() {
    return myOptions.ADD_CARETS_ON_DOUBLE_CTRL;
  }

  public void setAddCaretsOnDoubleCtrl(boolean val) {
    myOptions.ADD_CARETS_ON_DOUBLE_CTRL = val;
  }

  public BidiTextDirection getBidiTextDirection() {
    return myOptions.BIDI_TEXT_DIRECTION;
  }

  public void setBidiTextDirection(BidiTextDirection direction) {
    myOptions.BIDI_TEXT_DIRECTION = direction;
  }

  public boolean isShowIntentionPreview() {
    return myOptions.SHOW_INTENTION_PREVIEW;
  }

  public void setShowIntentionPreview(boolean show) {
    myOptions.SHOW_INTENTION_PREVIEW = show;
  }

  /**
   * @deprecated use {@link com.intellij.codeInsight.hints.HintUtilsKt#isParameterHintsEnabledForLanguage(Language)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean isShowParameterNameHints() {
    return myOptions.SHOW_PARAMETER_NAME_HINTS;
  }

  /**
   * @deprecated use {@link com.intellij.codeInsight.hints.HintUtilsKt#setShowParameterHintsForLanguage(boolean, Language)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void setShowParameterNameHints(boolean value) {
    myOptions.SHOW_PARAMETER_NAME_HINTS = value;
  }

  public boolean isKeepTrailingSpacesOnCaretLine() {
    return myOptions.KEEP_TRAILING_SPACE_ON_CARET_LINE;
  }

  public void setKeepTrailingSpacesOnCaretLine(boolean keep) {
    myOptions.KEEP_TRAILING_SPACE_ON_CARET_LINE = keep;
  }

  @NotNull
  public String getSoftWrapFileMasks() {
    String storedValue = myOptions.SOFT_WRAP_FILE_MASKS;
    if (storedValue != null) {
      return storedValue;
    }
    return isUseSoftWraps() ? SOFT_WRAP_FILE_MASKS_ENABLED_DEFAULT : SOFT_WRAP_FILE_MASKS_DISABLED_DEFAULT;
  }

  public void setSoftWrapFileMasks(@NotNull String value) {
    myOptions.SOFT_WRAP_FILE_MASKS = value;
  }

  @NotNull
  public CaretStopOptions getCaretStopOptions() {
    return myOsSpecificState.CARET_STOP_OPTIONS;
  }

  public void setCaretStopOptions(@NotNull CaretStopOptions options) {
    myOsSpecificState.CARET_STOP_OPTIONS = options;
  }

  public boolean isUseEditorFontInInlays() {
    return myOptions.USE_EDITOR_FONT_IN_INLAYS;
  }

  public void setUseEditorFontInInlays(boolean value) {
    myOptions.USE_EDITOR_FONT_IN_INLAYS = value;
  }
}
