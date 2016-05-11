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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.EnumSet;
import java.util.Set;

@State(name = "EditorSettings", storages = @Storage("editor.xml"))
public class EditorSettingsExternalizable implements PersistentStateComponent<EditorSettingsExternalizable.OptionSet> {
  //Q: make it interface?
  public static final class OptionSet {
    public String LINE_SEPARATOR;
    public String USE_SOFT_WRAPS;
    public boolean USE_CUSTOM_SOFT_WRAP_INDENT = false;
    public int CUSTOM_SOFT_WRAP_INDENT = 0;
    public boolean IS_VIRTUAL_SPACE = false;
    public boolean IS_CARET_INSIDE_TABS;
    @NonNls public String STRIP_TRAILING_SPACES = STRIP_TRAILING_SPACES_CHANGED;
    public boolean IS_ENSURE_NEWLINE_AT_EOF = false;
    public boolean SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = false;
    public long QUICK_DOC_ON_MOUSE_OVER_DELAY_MS = 500;
    public boolean SHOW_INTENTION_BULB = true;
    public boolean IS_CARET_BLINKING = true;
    public int CARET_BLINKING_PERIOD = 500;
    public boolean IS_RIGHT_MARGIN_SHOWN = true;
    public boolean ARE_LINE_NUMBERS_SHOWN = true;
    public boolean ARE_GUTTER_ICONS_SHOWN = true;
    public boolean IS_FOLDING_OUTLINE_SHOWN = true;
    public boolean SHOW_BREADCRUMBS = true;

    public boolean SMART_HOME = true;

    public boolean IS_BLOCK_CURSOR = false;
    public boolean IS_WHITESPACES_SHOWN = false;
    public boolean IS_LEADING_WHITESPACES_SHOWN = true;
    public boolean IS_INNER_WHITESPACES_SHOWN = true;
    public boolean IS_TRAILING_WHITESPACES_SHOWN = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_ALL_SOFTWRAPS_SHOWN = false;
    public boolean IS_INDENT_GUIDES_SHOWN = true;
    public boolean IS_ANIMATED_SCROLLING = true;
    public boolean IS_CAMEL_WORDS = false;
    public boolean ADDITIONAL_PAGE_AT_BOTTOM = false;

    public boolean IS_DND_ENABLED = true;
    @SuppressWarnings("SpellCheckingInspection")
    public boolean IS_WHEEL_FONTCHANGE_ENABLED = false;
    public boolean IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = true;

    public boolean RENAME_VARIABLES_INPLACE = true;
    public boolean PRESELECT_RENAME = true;
    public boolean SHOW_INLINE_DIALOG = true;
    
    public boolean REFRAIN_FROM_SCROLLING = false;

    public boolean SHOW_NOTIFICATION_AFTER_REFORMAT_CODE_ACTION = true;
    public boolean SHOW_NOTIFICATION_AFTER_OPTIMIZE_IMPORTS_ACTION = true;
    
    public boolean ADD_CARETS_ON_DOUBLE_CTRL = true;
  }

  private static final String COMPOSITE_PROPERTY_SEPARATOR = ":";

  private final Set<SoftWrapAppliancePlaces> myPlacesToUseSoftWraps = EnumSet.noneOf(SoftWrapAppliancePlaces.class);
  private OptionSet myOptions = new OptionSet();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private int myBlockIndent;
  //private int myTabSize = 4;
  //private boolean myUseTabCharacter = false;

  private int myAdditionalLinesCount = 10;
  private int myAdditionalColumnsCount = 20;
  private boolean myLineMarkerAreaShown = true;

  @NonNls public static final String STRIP_TRAILING_SPACES_NONE = "None";
  @NonNls public static final String STRIP_TRAILING_SPACES_CHANGED = "Changed";
  @NonNls public static final String STRIP_TRAILING_SPACES_WHOLE = "Whole";

  @MagicConstant(stringValues = {STRIP_TRAILING_SPACES_NONE, STRIP_TRAILING_SPACES_CHANGED, STRIP_TRAILING_SPACES_WHOLE})
  public @interface StripTrailingSpaces {}

  @NonNls public static final String DEFAULT_FONT_NAME = "Courier";

  public static EditorSettingsExternalizable getInstance() {
    if (ApplicationManager.getApplication().isDisposed()) {
      return new EditorSettingsExternalizable();
    }
    else {
      return ServiceManager.getService(EditorSettingsExternalizable.class);
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Nullable
  @Override
  public OptionSet getState() {
    return myOptions;
  }

  @Override
  public void loadState(OptionSet state) {
    myOptions = state;
    parseRawSoftWraps();
  }

  private void parseRawSoftWraps() {
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

  public int getAdditionalLinesCount() {
    return myAdditionalLinesCount;
  }

  public void setAdditionalLinesCount(int additionalLinesCount) {
    myAdditionalLinesCount = additionalLinesCount;
  }

  @SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection"})
  public int getAdditinalColumnsCount() {
    return myAdditionalColumnsCount;
  }

  public void setAdditionalColumnsCount(int value) {
    myAdditionalColumnsCount = value;
  }

  public boolean isLineMarkerAreaShown() {
    return myLineMarkerAreaShown;
  }

  public void setLineMarkerAreaShown(boolean lineMarkerAreaShown) {
    myLineMarkerAreaShown = lineMarkerAreaShown;
  }

  public boolean isFoldingOutlineShown() {
    return myOptions.IS_FOLDING_OUTLINE_SHOWN;
  }

  public void setFoldingOutlineShown(boolean val) {
    myOptions.IS_FOLDING_OUTLINE_SHOWN = val;
  }

  public boolean isBreadcrumbsShown() {
     return myOptions.SHOW_BREADCRUMBS;
  }

  public void setBreadcrumbsShown(boolean breadcrumbsShown) {
    myOptions.SHOW_BREADCRUMBS = breadcrumbsShown;
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
    myOptions.IS_VIRTUAL_SPACE = val;
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
    return myOptions.CARET_BLINKING_PERIOD;
  }

  public void setBlinkPeriod(int blinkInterval) {
    myOptions.CARET_BLINKING_PERIOD = blinkInterval;
  }


  public boolean isEnsureNewLineAtEOF() {
    return myOptions.IS_ENSURE_NEWLINE_AT_EOF;
  }

  public void setEnsureNewLineAtEOF(boolean ensure) {
    myOptions.IS_ENSURE_NEWLINE_AT_EOF = ensure;
  }

  @StripTrailingSpaces
  public String getStripTrailingSpaces() {
    return myOptions.STRIP_TRAILING_SPACES;
  } // TODO: move to CodeEditorManager or something else

  public void setStripTrailingSpaces(@StripTrailingSpaces String stripTrailingSpaces) {
    myOptions.STRIP_TRAILING_SPACES = stripTrailingSpaces;
  }

  public boolean isShowQuickDocOnMouseOverElement() {
    return myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT;
  }

  public void setShowQuickDocOnMouseOverElement(boolean show) {
    myOptions.SHOW_QUICK_DOC_ON_MOUSE_OVER_ELEMENT = show;
  }

  public long getQuickDocOnMouseOverElementDelayMillis() {
    return myOptions.QUICK_DOC_ON_MOUSE_OVER_DELAY_MS;
  }

  public void setQuickDocOnMouseOverElementDelayMillis(long delay) throws IllegalArgumentException {
    if (delay <= 0) {
      throw new IllegalArgumentException(String.format(
        "Non-positive delay for the 'show quick doc on mouse over element' value detected! Expected positive value but got %d",
        delay
      ));
    }
    myOptions.QUICK_DOC_ON_MOUSE_OVER_DELAY_MS = delay;
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
}
