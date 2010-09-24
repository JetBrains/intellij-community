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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 19, 2002
 * Time: 3:19:05 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class SettingsImpl implements EditorSettings {
  @Nullable private final EditorEx myEditor;
  private Boolean myIsCamelWords;

  public SettingsImpl(@Nullable EditorEx editor) {
    myEditor = editor;
  }

  // This group of settings does not have UI
  private int     myAdditionalLinesCount          = 5;
  private int     myAdditionalColumnsCount        = 3;
  private int     myLineCursorWidth               = 2;
  private boolean myLineMarkerAreaShown           = true;
  private boolean myAllowSingleLogicalLineFolding = false;
  private boolean myForceScrollToEnd              = true;

  // These comes from CodeStyleSettings
  private Integer myTabSize         = null;
  private Integer myCachedTabSize   = null;
  private Boolean myUseTabCharacter = null;

  // These comes from EditorSettingsExternalizable defaults.
  private Boolean myIsVirtualSpace                        = null;
  private Boolean myIsCaretInsideTabs                     = null;
  private Boolean myIsCaretBlinking                       = null;
  private Integer myCaretBlinkingPeriod                   = null;
  private Boolean myIsRightMarginShown                    = null;
  private Integer myRightMargin                           = null;
  private Boolean myAreLineNumbersShown                   = null;
  private Boolean myIsFoldingOutlineShown                 = null;
  private Boolean myIsSmartHome                           = null;
  private Boolean myIsBlockCursor                         = null;
  private Boolean myIsWhitespacesShown                    = null;
  private Boolean myIndentGuidesShown                     = null;
  private Boolean myIsAnimatedScrolling                   = null;
  private Boolean myIsAdditionalPageAtBottom              = null;
  private Boolean myIsDndEnabled                          = null;
  private Boolean myIsWheelFontChangeEnabled              = null;
  private Boolean myIsMouseClickSelectionHonorsCamelWords = null;
  private Boolean myIsRenameVariablesInplace              = null;
  private Boolean myIsRefrainFromScrolling                = null;
  private Boolean myUseSoftWraps                          = null;
  private Boolean myIsAllSoftWrapsShown                   = null;

  public boolean isRightMarginShown() {
    return myIsRightMarginShown != null
           ? myIsRightMarginShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isRightMarginShown();
  }

  public void setRightMarginShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsRightMarginShown)) return;
    myIsRightMarginShown = newValue;
    fireEditorRefresh();
  }

  public boolean isWhitespacesShown() {
    return myIsWhitespacesShown != null
           ? myIsWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWhitespacesShown();
  }

  public void setWhitespacesShown(boolean val) {
    myIsWhitespacesShown = Boolean.valueOf(val);
  }

  public boolean isIndentGuidesShown() {
    return myIndentGuidesShown != null
           ? myIndentGuidesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isIndentGuidesShown();
  }

  public void setIndentGuidesShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIndentGuidesShown)) return;

    myIndentGuidesShown = newValue;
    fireEditorRefresh();
  }

  public boolean isLineNumbersShown() {
    return myAreLineNumbersShown != null
           ? myAreLineNumbersShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isLineNumbersShown();
  }

  public void setLineNumbersShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myAreLineNumbersShown)) return;
    myAreLineNumbersShown = newValue;
    fireEditorRefresh();
  }

  public int getRightMargin(Project project) {
    return myRightMargin != null ? myRightMargin.intValue() :
           CodeStyleFacade.getInstance(project).getRightMargin();
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin(Project project) {
    return CodeStyleFacade.getInstance(project).isWrapWhenTypingReachesRightMargin();
  }

  public void setRightMargin(int rightMargin) {
    final Integer newValue = Integer.valueOf(rightMargin);
    if (newValue.equals(myRightMargin)) return;
    myRightMargin = newValue;
    fireEditorRefresh();
  }

  public int getAdditionalLinesCount() {
    return myAdditionalLinesCount;
  }

  public void setAdditionalLinesCount(int additionalLinesCount) {
    if (myAdditionalLinesCount == additionalLinesCount) return;
    myAdditionalLinesCount = additionalLinesCount;
    fireEditorRefresh();
  }

  public int getAdditionalColumnsCount() {
    return myAdditionalColumnsCount;
  }

  public void setAdditionalColumnsCount(int additinalColumnsCount) {
    if (myAdditionalColumnsCount == additinalColumnsCount) return;
    myAdditionalColumnsCount = additinalColumnsCount;
    fireEditorRefresh();
  }

  public boolean isLineMarkerAreaShown() {
    return myLineMarkerAreaShown;
  }

  public void setLineMarkerAreaShown(boolean lineMarkerAreaShown) {
    if (myLineMarkerAreaShown == lineMarkerAreaShown) return;
    myLineMarkerAreaShown = lineMarkerAreaShown;
    fireEditorRefresh();
  }

  public boolean isFoldingOutlineShown() {
    return myIsFoldingOutlineShown != null
           ? myIsFoldingOutlineShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isFoldingOutlineShown();
  }

  public void setFoldingOutlineShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsFoldingOutlineShown)) return;
    myIsFoldingOutlineShown = newValue;
    fireEditorRefresh();
  }

  public boolean isUseTabCharacter(Project project) {
    FileType fileType = getFileType();
    return myUseTabCharacter != null ? myUseTabCharacter.booleanValue() : CodeStyleFacade.getInstance(project).useTabCharacter(fileType);
  }

  public void setUseTabCharacter(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myUseTabCharacter)) return;
    myUseTabCharacter = newValue;
    fireEditorRefresh();
  }

  public void reinitSettings() {
    myCachedTabSize = null;
  }

  public int getTabSize(Project project) {
    if (myTabSize != null) return myTabSize.intValue();
    if (myCachedTabSize != null) return myCachedTabSize.intValue();

    FileType fileType = getFileType();
    int tabSize = project == null || project.isDisposed() ? 0 : CodeStyleFacade.getInstance(project).getTabSize(fileType);
    myCachedTabSize = Integer.valueOf(tabSize);
    return tabSize;
  }

  @Nullable
  private FileType getFileType() {
    VirtualFile file = myEditor == null ? null : myEditor.getVirtualFile();
    return file == null ? null : file.getFileType();
  }

  public void setTabSize(int tabSize) {
    final Integer newValue = Integer.valueOf(tabSize);
    if (newValue.equals(myTabSize)) return;
    myTabSize = newValue;
    fireEditorRefresh();
  }

  public boolean isSmartHome() {
    return myIsSmartHome != null
           ? myIsSmartHome.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmartHome();
  }

  public void setSmartHome(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsSmartHome)) return;
    myIsSmartHome = newValue;
    fireEditorRefresh();
  }

  public boolean isVirtualSpace() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsVirtualSpace != null
           ? myIsVirtualSpace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVirtualSpace();
  }

  public void setVirtualSpace(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsVirtualSpace)) return;
    myIsVirtualSpace = newValue;
    fireEditorRefresh();
  }

  public boolean isAdditionalPageAtBottom() {
    return myIsAdditionalPageAtBottom != null
           ? myIsAdditionalPageAtBottom.booleanValue()
           : EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom();
  }

  public void setAdditionalPageAtBottom(boolean val) {
    myIsAdditionalPageAtBottom = Boolean.valueOf(val);
  }

  public boolean isCaretInsideTabs() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsCaretInsideTabs != null
           ? myIsCaretInsideTabs.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCaretInsideTabs();
  }

  public void setCaretInsideTabs(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsCaretInsideTabs)) return;
    myIsCaretInsideTabs = newValue;
    fireEditorRefresh();
  }

  public boolean isBlockCursor() {
    return myIsBlockCursor != null
           ? myIsBlockCursor.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlockCursor();
  }

  public void setBlockCursor(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsBlockCursor)) return;
    myIsBlockCursor = newValue;
    fireEditorRefresh();
  }

  public int getLineCursorWidth() {
    return myLineCursorWidth;
  }

  public void setLineCursorWidth(int width) {
    myLineCursorWidth = width;
  }

  public boolean isAnimatedScrolling() {
    return myIsAnimatedScrolling != null
           ? myIsAnimatedScrolling.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmoothScrolling();
  }

  public void setAnimatedScrolling(boolean val) {
    myIsAnimatedScrolling = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isCamelWords() {
    return myIsCamelWords != null
           ? myIsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCamelWords();
  }

  public void setCamelWords(boolean val) {
    myIsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isBlinkCaret() {
    return myIsCaretBlinking != null
           ? myIsCaretBlinking.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlinkCaret();
  }

  public void setBlinkCaret(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsCaretBlinking)) return;
    myIsCaretBlinking = newValue;
    fireEditorRefresh();
  }

  public int getCaretBlinkPeriod() {
    return myCaretBlinkingPeriod != null
           ? myCaretBlinkingPeriod.intValue()
           : EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }

  public void setCaretBlinkPeriod(int blinkPeriod) {
    final Integer newValue = Integer.valueOf(blinkPeriod);
    if (newValue.equals(myCaretBlinkingPeriod)) return;
    myCaretBlinkingPeriod = newValue;
    fireEditorRefresh();
  }

  public boolean isDndEnabled() {
    return myIsDndEnabled != null ? myIsDndEnabled.booleanValue() : EditorSettingsExternalizable.getInstance().isDndEnabled();
  }

  public void setDndEnabled(boolean val) {
    myIsDndEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isWheelFontChangeEnabled() {
    return myIsWheelFontChangeEnabled != null
           ? myIsWheelFontChangeEnabled.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled();
  }

  public void setWheelFontChangeEnabled(boolean val) {
    myIsWheelFontChangeEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isMouseClickSelectionHonorsCamelWords() {
    return myIsMouseClickSelectionHonorsCamelWords != null
           ? myIsMouseClickSelectionHonorsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords();
  }

  public void setMouseClickSelectionHonorsCamelWords(boolean val) {
    myIsMouseClickSelectionHonorsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isVariableInplaceRenameEnabled() {
    return myIsRenameVariablesInplace != null
           ? myIsRenameVariablesInplace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled();
  }

  public void setVariableInplaceRenameEnabled(boolean val) {
    myIsRenameVariablesInplace = val? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isRefrainFromScrolling() {
    if (myIsRefrainFromScrolling != null) return myIsRefrainFromScrolling.booleanValue();
    return EditorSettingsExternalizable.getInstance().isRefrainFromScrolling();
  }


  public void setRefrainFromScrolling(boolean b) {
    myIsRefrainFromScrolling = b ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isUseSoftWraps() {
    return myUseSoftWraps != null ? myUseSoftWraps.booleanValue()
                                  : EditorSettingsExternalizable.getInstance().isUseSoftWraps();
  }

  public void setUseSoftWraps(boolean use) {
    final Boolean newValue = use ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myUseSoftWraps)) return;
    myUseSoftWraps = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isAllSoftWrapsShown() {
    return myIsAllSoftWrapsShown != null ? myIsWhitespacesShown.booleanValue()
                                      : EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown();
  }

  @Override
  public boolean isUseCustomSoftWrapIndent() {
    return EditorSettingsExternalizable.getInstance().isUseCustomSoftWrapIndent();
  }

  @Override
  public int getCustomSoftWrapIndent() {
    return EditorSettingsExternalizable.getInstance().getCustomSoftWrapIndent();
  }

  @Override
  public boolean isAllowSingleLogicalLineFolding() {
    return myAllowSingleLogicalLineFolding;
  }

  @Override
  public void setAllowSingleLogicalLineFolding(boolean allow) {
    myAllowSingleLogicalLineFolding = allow;
  }

  @Override
  public boolean isForceScrollToEnd() {
    return myForceScrollToEnd;
  }

  @Override
  public void setForceScrollToEnd(final boolean value) {
    myForceScrollToEnd = value;
  }

  private void fireEditorRefresh() {
    if (myEditor != null) {
      myEditor.reinitSettings();
    }
  }
}
