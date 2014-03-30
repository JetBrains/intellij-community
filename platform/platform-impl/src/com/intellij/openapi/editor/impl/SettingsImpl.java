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
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class SettingsImpl implements EditorSettings {
  @Nullable private final EditorEx myEditor;
  private Boolean myIsCamelWords;

  // This group of settings does not have UI
  private SoftWrapAppliancePlaces mySoftWrapAppliancePlace        = SoftWrapAppliancePlaces.MAIN_EDITOR;
  private int                     myAdditionalLinesCount          = 5;
  private int                     myAdditionalColumnsCount        = 3;
  private int                     myLineCursorWidth               = 2;
  private boolean                 myLineMarkerAreaShown           = true;
  private boolean                 myAllowSingleLogicalLineFolding = false;

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
  private Boolean myUseCustomSoftWrapIndent               = null;
  private Integer myCustomSoftWrapIndent                  = null;
  private Boolean myRenamePreselect                       = null;
  private Boolean myWrapWhenTypingReachesRightMargin      = null;
  private Boolean myShowIntentionBulb                     = null;

  public SettingsImpl(@Nullable EditorEx editor) {
    myEditor = editor;
  }
  
  @Override
  public boolean isRightMarginShown() {
    return myIsRightMarginShown != null
           ? myIsRightMarginShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isRightMarginShown();
  }

  @Override
  public void setRightMarginShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsRightMarginShown)) return;
    myIsRightMarginShown = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isWhitespacesShown() {
    return myIsWhitespacesShown != null
           ? myIsWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWhitespacesShown();
  }

  @Override
  public void setWhitespacesShown(boolean val) {
    myIsWhitespacesShown = Boolean.valueOf(val);
  }

  @Override
  public boolean isIndentGuidesShown() {
    return myIndentGuidesShown != null
           ? myIndentGuidesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isIndentGuidesShown();
  }

  @Override
  public void setIndentGuidesShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIndentGuidesShown)) return;

    myIndentGuidesShown = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isLineNumbersShown() {
    return myAreLineNumbersShown != null
           ? myAreLineNumbersShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isLineNumbersShown();
  }

  @Override
  public void setLineNumbersShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myAreLineNumbersShown)) return;
    myAreLineNumbersShown = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getRightMargin(Project project) {
    return myRightMargin != null ? myRightMargin.intValue() :
           CodeStyleFacade.getInstance(project).getRightMargin();
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin(Project project) {
    return myWrapWhenTypingReachesRightMargin != null ?
           myWrapWhenTypingReachesRightMargin.booleanValue() :
           CodeStyleFacade.getInstance(project).isWrapWhenTypingReachesRightMargin();
  }

  @Override
  public void setWrapWhenTypingReachesRightMargin(boolean val) {
    myWrapWhenTypingReachesRightMargin = val;
  }

  @Override
  public void setRightMargin(int rightMargin) {
    final Integer newValue = Integer.valueOf(rightMargin);
    if (newValue.equals(myRightMargin)) return;
    myRightMargin = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getAdditionalLinesCount() {
    return myAdditionalLinesCount;
  }

  @Override
  public void setAdditionalLinesCount(int additionalLinesCount) {
    if (myAdditionalLinesCount == additionalLinesCount) return;
    myAdditionalLinesCount = additionalLinesCount;
    fireEditorRefresh();
  }

  @Override
  public int getAdditionalColumnsCount() {
    return myAdditionalColumnsCount;
  }

  @Override
  public void setAdditionalColumnsCount(int additionalColumnsCount) {
    if (myAdditionalColumnsCount == additionalColumnsCount) return;
    myAdditionalColumnsCount = additionalColumnsCount;
    fireEditorRefresh();
  }

  @Override
  public boolean isLineMarkerAreaShown() {
    return myLineMarkerAreaShown;
  }

  @Override
  public void setLineMarkerAreaShown(boolean lineMarkerAreaShown) {
    if (myLineMarkerAreaShown == lineMarkerAreaShown) return;
    myLineMarkerAreaShown = lineMarkerAreaShown;
    fireEditorRefresh();
  }

  @Override
  public boolean isFoldingOutlineShown() {
    return myIsFoldingOutlineShown != null
           ? myIsFoldingOutlineShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isFoldingOutlineShown();
  }

  @Override
  public void setFoldingOutlineShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsFoldingOutlineShown)) return;
    myIsFoldingOutlineShown = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isUseTabCharacter(Project project) {
    FileType fileType = getFileType();
    return myUseTabCharacter != null ? myUseTabCharacter.booleanValue() : CodeStyleFacade.getInstance(project).useTabCharacter(fileType);
  }

  @Override
  public void setUseTabCharacter(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myUseTabCharacter)) return;
    myUseTabCharacter = newValue;
    fireEditorRefresh();
  }

  public void setSoftWrapAppliancePlace(SoftWrapAppliancePlaces softWrapAppliancePlace) {
    mySoftWrapAppliancePlace = softWrapAppliancePlace;
  }

  public void reinitSettings() {
    myCachedTabSize = null;
  }

  @Override
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

  @Override
  public void setTabSize(int tabSize) {
    final Integer newValue = Integer.valueOf(tabSize);
    if (newValue.equals(myTabSize)) return;
    myTabSize = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isSmartHome() {
    return myIsSmartHome != null
           ? myIsSmartHome.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmartHome();
  }

  @Override
  public void setSmartHome(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsSmartHome)) return;
    myIsSmartHome = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isVirtualSpace() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsVirtualSpace != null
           ? myIsVirtualSpace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVirtualSpace();
  }

  @Override
  public void setVirtualSpace(boolean allow) {
    final Boolean newValue = allow;
    if (newValue.equals(myIsVirtualSpace)) return;
    myIsVirtualSpace = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isAdditionalPageAtBottom() {
    return myIsAdditionalPageAtBottom != null
           ? myIsAdditionalPageAtBottom.booleanValue()
           : EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom();
  }

  @Override
  public void setAdditionalPageAtBottom(boolean val) {
    myIsAdditionalPageAtBottom = Boolean.valueOf(val);
  }

  @Override
  public boolean isCaretInsideTabs() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsCaretInsideTabs != null
           ? myIsCaretInsideTabs.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCaretInsideTabs();
  }

  @Override
  public void setCaretInsideTabs(boolean allow) {
    final Boolean newValue = allow;
    if (newValue.equals(myIsCaretInsideTabs)) return;
    myIsCaretInsideTabs = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isBlockCursor() {
    return myIsBlockCursor != null
           ? myIsBlockCursor.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlockCursor();
  }

  @Override
  public void setBlockCursor(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsBlockCursor)) return;
    myIsBlockCursor = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getLineCursorWidth() {
    return myLineCursorWidth;
  }

  @Override
  public void setLineCursorWidth(int width) {
    myLineCursorWidth = width;
  }

  @Override
  public boolean isAnimatedScrolling() {
    return myIsAnimatedScrolling != null
           ? myIsAnimatedScrolling.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmoothScrolling();
  }

  @Override
  public void setAnimatedScrolling(boolean val) {
    myIsAnimatedScrolling = val ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isCamelWords() {
    return myIsCamelWords != null
           ? myIsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCamelWords();
  }

  @Override
  public void setCamelWords(boolean val) {
    myIsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public void resetCamelWords() {
    myIsCamelWords = null;
  }

  @Override
  public boolean isBlinkCaret() {
    return myIsCaretBlinking != null
           ? myIsCaretBlinking.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlinkCaret();
  }

  @Override
  public void setBlinkCaret(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myIsCaretBlinking)) return;
    myIsCaretBlinking = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getCaretBlinkPeriod() {
    return myCaretBlinkingPeriod != null
           ? myCaretBlinkingPeriod.intValue()
           : EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }

  @Override
  public void setCaretBlinkPeriod(int blinkPeriod) {
    final Integer newValue = Integer.valueOf(blinkPeriod);
    if (newValue.equals(myCaretBlinkingPeriod)) return;
    myCaretBlinkingPeriod = newValue;
    fireEditorRefresh();
  }

  @Override
  public boolean isDndEnabled() {
    return myIsDndEnabled != null ? myIsDndEnabled.booleanValue() : EditorSettingsExternalizable.getInstance().isDndEnabled();
  }

  @Override
  public void setDndEnabled(boolean val) {
    myIsDndEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isWheelFontChangeEnabled() {
    return myIsWheelFontChangeEnabled != null
           ? myIsWheelFontChangeEnabled.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled();
  }

  @Override
  public void setWheelFontChangeEnabled(boolean val) {
    myIsWheelFontChangeEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isMouseClickSelectionHonorsCamelWords() {
    return myIsMouseClickSelectionHonorsCamelWords != null
           ? myIsMouseClickSelectionHonorsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords();
  }

  @Override
  public void setMouseClickSelectionHonorsCamelWords(boolean val) {
    myIsMouseClickSelectionHonorsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isVariableInplaceRenameEnabled() {
    return myIsRenameVariablesInplace != null
           ? myIsRenameVariablesInplace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled();
  }

  @Override
  public void setVariableInplaceRenameEnabled(boolean val) {
    myIsRenameVariablesInplace = val? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isRefrainFromScrolling() {
    if (myIsRefrainFromScrolling != null) return myIsRefrainFromScrolling.booleanValue();
    return EditorSettingsExternalizable.getInstance().isRefrainFromScrolling();
  }


  @Override
  public void setRefrainFromScrolling(boolean b) {
    myIsRefrainFromScrolling = b ? Boolean.TRUE : Boolean.FALSE;
  }

  @Override
  public boolean isUseSoftWraps() {
    return myUseSoftWraps != null ? myUseSoftWraps.booleanValue()
                                  : EditorSettingsExternalizable.getInstance().isUseSoftWraps(mySoftWrapAppliancePlace);
  }

  @Override
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
    return myUseCustomSoftWrapIndent == null ? EditorSettingsExternalizable.getInstance().isUseCustomSoftWrapIndent()
                                             : myUseCustomSoftWrapIndent;
  }

  @Override
  public void setUseCustomSoftWrapIndent(boolean useCustomSoftWrapIndent) {
    myUseCustomSoftWrapIndent = useCustomSoftWrapIndent;
  }

  @Override
  public int getCustomSoftWrapIndent() {
    return myCustomSoftWrapIndent == null ? EditorSettingsExternalizable.getInstance().getCustomSoftWrapIndent() : myCustomSoftWrapIndent;
  }

  @Override
  public void setCustomSoftWrapIndent(int indent) {
    myCustomSoftWrapIndent = indent;
  }

  @Override
  public boolean isAllowSingleLogicalLineFolding() {
    return myAllowSingleLogicalLineFolding;
  }

  @Override
  public void setAllowSingleLogicalLineFolding(boolean allow) {
    myAllowSingleLogicalLineFolding = allow;
  }

  private void fireEditorRefresh() {
    if (myEditor != null) {
      myEditor.reinitSettings();
    }
  }

  @Override
  public boolean isPreselectRename() {
    return myRenamePreselect == null ? EditorSettingsExternalizable.getInstance().isPreselectRename() : myRenamePreselect;
  }

  @Override
  public void setPreselectRename(boolean val) {
    myRenamePreselect = val;
  }

  @Override
  public boolean isShowIntentionBulb() {
    return myShowIntentionBulb == null ? EditorSettingsExternalizable.getInstance().isShowIntentionBulb() : myShowIntentionBulb;
  }

  @Override
  public void setShowIntentionBulb(boolean show) {
    myShowIntentionBulb = show; 
  }
}
