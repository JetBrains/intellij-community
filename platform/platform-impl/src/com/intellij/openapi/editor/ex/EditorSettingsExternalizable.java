/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

public class EditorSettingsExternalizable implements NamedJDOMExternalizable, ExportableApplicationComponent, Cloneable {
  //Q: make it interface?
  public static class OptionSet implements Cloneable {
    public String LINE_SEPARATOR;
    public boolean USE_SOFT_WRAPS = true;
    public boolean IS_VIRTUAL_SPACE = true;
    public boolean IS_CARET_INSIDE_TABS;
    @NonNls public String STRIP_TRAILING_SPACES = "Changed";
    public boolean IS_ENSURE_NEWLINE_AT_EOF = false;
    public boolean IS_CARET_BLINKING = true;
    public int CARET_BLINKING_PERIOD = 500;
    public boolean IS_RIGHT_MARGIN_SHOWN = true;
    public boolean ARE_LINE_NUMBERS_SHOWN = false;
    public boolean IS_FOLDING_OUTLINE_SHOWN = true;

    public boolean SMART_HOME = true;

    public boolean IS_BLOCK_CURSOR = false;
    public boolean IS_WHITESPACES_SHOWN = false;
    public boolean IS_INDENT_GUIDES_SHOWN = true;
    public boolean IS_ANIMATED_SCROLLING = true;
    public boolean IS_CAMEL_WORDS = false;
    public boolean ADDITIONAL_PAGE_AT_BOTTOM = false;

    public boolean IS_DND_ENABLED = true;
    public boolean IS_WHEEL_FONTCHANGE_ENABLED = true;
    public boolean IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS = true;
    @Deprecated
    public boolean IS_NATIVE2ASCII_FOR_PROPERTIES_FILES;
    @Deprecated
    public String DEFAULT_PROPERTIES_FILES_CHARSET_NAME;

    public boolean RENAME_VARIABLES_INPLACE = true;
    public boolean REFRAIN_FROM_SCROLLING = false;

    public boolean SHOW_REFORMAT_DIALOG = true;
    public boolean SHOW_OPIMIZE_IMPORTS_DIALOG = true;

    public Object clone() {
      try {
        return super.clone();
      } catch (CloneNotSupportedException e) {
        return null;
      }
    }
  }

  private OptionSet myOptions = new OptionSet();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private int myBlockIndent;
  //private int myTabSize = 4;
  //private boolean myUseTabCharacter = false;

  private int myAdditionalLinesCount = 10;
  private int myAdditinalColumnsCount = 20;
  private boolean myLineMarkerAreaShown = true;

  @NonNls public static final String STRIP_TRAILING_SPACES_NONE = "None";
  @NonNls public static final String STRIP_TRAILING_SPACES_CHANGED = "Changed";
  @NonNls public static final String STRIP_TRAILING_SPACES_WHOLE = "Whole";


  @NonNls public static final String DEFAULT_FONT_NAME = "Courier";

  public static EditorSettingsExternalizable getInstance() {
    final Application app = ApplicationManager.getApplication();
    if (app.isDisposed()) return new EditorSettingsExternalizable();
    return app.getComponent(EditorSettingsExternalizable.class);
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void addPropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(myOptions, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(myOptions, element, new DefaultJDOMExternalizer.JDOMFilter() {
      public boolean isAccept(final Field field) {
        return !field.getName().equals("IS_NATIVE2ASCII_FOR_PROPERTIES_FILES") && !field.getName().equals("DEFAULT_PROPERTIES_FILES_CHARSET_NAME");
      }
    });
  }

  public String getExternalFileName() {
    return "editor";
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

  public int getAdditionalLinesCount() {
    return myAdditionalLinesCount;
  }

  public void setAdditionalLinesCount(int additionalLinesCount) {
    myAdditionalLinesCount = additionalLinesCount;
  }

  public int getAdditinalColumnsCount() {
    return myAdditinalColumnsCount;
  }

  public void setAdditionalColumnsCount(int additinalColumnsCount) {
    myAdditinalColumnsCount = additinalColumnsCount;
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

  public boolean isBlockCursor() {
    return myOptions.IS_BLOCK_CURSOR;
  }

  public void setBlockCursor(boolean val) {
    myOptions.IS_BLOCK_CURSOR = val;
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
    return myOptions.USE_SOFT_WRAPS;
  }

  public void setUseSoftWraps(boolean use) {
    myOptions.USE_SOFT_WRAPS = use;
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

  public String getStripTrailingSpaces() {
    return myOptions.STRIP_TRAILING_SPACES;
  } // TODO: move to CodeEditorManager or something else

  public void setStripTrailingSpaces(String stripTrailingSpaces) {
    myOptions.STRIP_TRAILING_SPACES = stripTrailingSpaces;
  }

  public boolean isRefrainFromScrolling() {
    return myOptions.REFRAIN_FROM_SCROLLING;
  }

  public void setRefrainFromScrolling(boolean b) {
    myOptions.REFRAIN_FROM_SCROLLING = b;
  }

  public Object clone() {
    EditorSettingsExternalizable copy = new EditorSettingsExternalizable();
    copy.myOptions = (OptionSet) myOptions.clone();
    copy.myBlockIndent = myBlockIndent;
    //copy.myTabSize = myTabSize;
    //copy.myUseTabCharacter = myUseTabCharacter;
    copy.myAdditionalLinesCount = myAdditionalLinesCount;
    copy.myAdditinalColumnsCount = myAdditinalColumnsCount;
    copy.myLineMarkerAreaShown = myLineMarkerAreaShown;

    return copy;
  }

  @NotNull
  public String getComponentName() {
    return "EditorSettings";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return OptionsBundle.message("options.editor.settings.presentable.name");
  }

  public boolean isWhitespacesShown() {
    return myOptions.IS_WHITESPACES_SHOWN;
  }

  public void setWhitespacesShown(boolean val) {
    myOptions.IS_WHITESPACES_SHOWN = val;
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

  // returns true if something has been migrated
  public boolean migrateCharsetSettingsTo(EncodingManager encodingManager) {
    if (myOptions.DEFAULT_PROPERTIES_FILES_CHARSET_NAME != null) {
      Charset charset = CharsetToolkit.forName(myOptions.DEFAULT_PROPERTIES_FILES_CHARSET_NAME);
      if (charset != null) {
        encodingManager.setDefaultCharsetForPropertiesFiles(null, charset);
        encodingManager.setNative2AsciiForPropertiesFiles(null, myOptions.IS_NATIVE2ASCII_FOR_PROPERTIES_FILES);
      }
      return true;
    }
    return false;
  }
}
