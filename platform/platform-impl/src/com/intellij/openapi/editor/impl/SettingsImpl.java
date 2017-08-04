/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.editor.impl;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SettingsImpl implements EditorSettings {
  private static final Logger LOG = Logger.getInstance(SettingsImpl.class);

  @Nullable private final EditorEx myEditor;
  @Nullable private final Language myLanguage;
  private Boolean myIsCamelWords;

  // This group of settings does not have UI
  private SoftWrapAppliancePlaces mySoftWrapAppliancePlace        = SoftWrapAppliancePlaces.MAIN_EDITOR;
  private int                     myAdditionalLinesCount          = Registry.intValue("editor.virtual.lines", 5);
  private int                     myAdditionalColumnsCount        = 3;
  private int                     myLineCursorWidth               = Registry.intValue("editor.caret.width", 2);
  private boolean                 myLineMarkerAreaShown           = true;
  private boolean                 myAllowSingleLogicalLineFolding = false;
  private boolean myAutoCodeFoldingEnabled = true;

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
  private Boolean myGutterIconsShown                      = null;
  private Boolean myIsFoldingOutlineShown                 = null;
  private Boolean myIsSmartHome                           = null;
  private Boolean myIsBlockCursor                         = null;
  private Boolean myCaretRowShown                         = null;
  private Boolean myIsWhitespacesShown                    = null;
  private Boolean myIsLeadingWhitespacesShown             = null;
  private Boolean myIsInnerWhitespacesShown               = null;
  private Boolean myIsTrailingWhitespacesShown            = null;
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
  
  public SettingsImpl() {
    this(null, null, null);
  }

  SettingsImpl(@Nullable EditorEx editor, @Nullable Project project, @Nullable EditorKind kind) {
    myEditor = editor;
    myLanguage = editor != null && project != null ? getDocumentLanguage(project, editor.getDocument()) : null;
    
    if (EditorKind.CONSOLE.equals(kind)) {
      mySoftWrapAppliancePlace = SoftWrapAppliancePlaces.CONSOLE;
    }
    else if (EditorKind.PREVIEW.equals(kind)) {
      mySoftWrapAppliancePlace = SoftWrapAppliancePlaces.PREVIEW;
    }
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
  public boolean isLeadingWhitespaceShown() {
    return myIsLeadingWhitespacesShown != null
           ? myIsLeadingWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isLeadingWhitespacesShown();
  }

  @Override
  public void setLeadingWhitespaceShown(boolean val) {
    myIsLeadingWhitespacesShown = Boolean.valueOf(val);
  }

  @Override
  public boolean isInnerWhitespaceShown() {
    return myIsInnerWhitespacesShown != null
           ? myIsInnerWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isInnerWhitespacesShown();
  }

  @Override
  public void setInnerWhitespaceShown(boolean val) {
    myIsInnerWhitespacesShown = Boolean.valueOf(val);
  }

  @Override
  public boolean isTrailingWhitespaceShown() {
    return myIsTrailingWhitespacesShown != null
           ? myIsTrailingWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isTrailingWhitespacesShown();
  }

  @Override
  public void setTrailingWhitespaceShown(boolean val) {
    myIsTrailingWhitespacesShown = Boolean.valueOf(val);
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
  public boolean areGutterIconsShown() {
    return myGutterIconsShown != null
           ? myGutterIconsShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().areGutterIconsShown();
  }

  @Override
  public void setGutterIconsShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myGutterIconsShown)) return;
    myGutterIconsShown = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getRightMargin(Project project) {
    return myRightMargin != null ? myRightMargin.intValue() :
           CodeStyleFacade.getInstance(project).getRightMargin(myLanguage);
  }

  @Nullable
  private static Language getDocumentLanguage(@NotNull Project project, @NotNull Document document) {
    if (!project.isDisposed()) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      PsiFile file = documentManager.getPsiFile(document);
      if (file != null) return file.getLanguage();
    }
    else {
      LOG.warn("Attempting to get a language for document on a disposed project: " + project.getName());
    }
    return null;
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin(Project project) {
    return myWrapWhenTypingReachesRightMargin != null ?
           myWrapWhenTypingReachesRightMargin.booleanValue() :
           CodeStyleFacade.getInstance(project).isWrapOnTyping(myLanguage);
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
  public boolean isAutoCodeFoldingEnabled() {
    return myAutoCodeFoldingEnabled;
  }

  @Override
  public void setAutoCodeFoldingEnabled(boolean val) {
    myAutoCodeFoldingEnabled = val;
  }

  @Override
  public boolean isUseTabCharacter(Project project) {
    PsiFile file = getPsiFile(project);
    return myUseTabCharacter != null
           ? myUseTabCharacter.booleanValue()
           : CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(file).USE_TAB_CHARACTER;
  }

  @Override
  public void setUseTabCharacter(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myUseTabCharacter)) return;
    myUseTabCharacter = newValue;
    fireEditorRefresh();
  }

  /**
   * @deprecated use {@link com.intellij.openapi.editor.EditorKind}
   */
  @Deprecated
  public void setSoftWrapAppliancePlace(SoftWrapAppliancePlaces softWrapAppliancePlace) {
    if (softWrapAppliancePlace != mySoftWrapAppliancePlace) {
      mySoftWrapAppliancePlace = softWrapAppliancePlace;
      fireEditorRefresh();
    }
  }

  /**
   * @deprecated use {@link com.intellij.openapi.editor.EditorKind}
   */
  @Deprecated
  public SoftWrapAppliancePlaces getSoftWrapAppliancePlace() {
    return mySoftWrapAppliancePlace;
  }

  public void reinitSettings() {
    myCachedTabSize = null;
    reinitDocumentIndentOptions();
  }

  private void reinitDocumentIndentOptions() {
    if (myEditor == null || myEditor.isViewer()) return;
    final Project project = myEditor.getProject();
    final DocumentEx document = myEditor.getDocument();

    if (project == null || project.isDisposed()) return;

    final PsiDocumentManager psiManager = PsiDocumentManager.getInstance(project);
    final PsiFile file = psiManager.getPsiFile(document);
    if (file == null) return;

    CodeStyleSettingsManager.updateDocumentIndentOptions(project, document);
  }

  @Override
  public int getTabSize(Project project) {
    if (myTabSize != null) return myTabSize.intValue();
    if (myCachedTabSize != null) return myCachedTabSize.intValue();
    int tabSize;
    try {
      if (project == null || project.isDisposed()) {
        tabSize = CodeStyleSettingsManager.getSettings(null).getTabSize(null);
      }
      else  {
        PsiFile file = getPsiFile(project);
        if (myEditor != null && myEditor.isViewer()) {
          FileType fileType = file != null ? file.getFileType() : null;
          tabSize = CodeStyleSettingsManager.getSettings(project).getIndentOptions(fileType).TAB_SIZE;
        } else {
          tabSize = CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(file).TAB_SIZE;
        }
      }
    }
    catch (Exception e) {
      LOG.error("Error determining tab size", e);
      tabSize = new CommonCodeStyleSettings.IndentOptions().TAB_SIZE;
    }
    myCachedTabSize = Integer.valueOf(Math.max(1, tabSize));
    return tabSize;
  }

  @Nullable
  private PsiFile getPsiFile(@Nullable Project project) {
    if (project != null && myEditor != null) {
      return PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument());
    }
    return null;
  }

  @Override
  public void setTabSize(int tabSize) {
    final Integer newValue = Integer.valueOf(Math.max(1, tabSize));
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
  public boolean isCaretRowShown() {
    return myCaretRowShown != null
           ? myCaretRowShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCaretRowShown();
  }

  @Override
  public void setCaretRowShown(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myCaretRowShown)) return;
    myCaretRowShown = newValue;
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
    return !SystemProperties.isTrueSmoothScrollingEnabled() && // uses its own interpolation
           myIsAnimatedScrolling != null
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
  
  void setUseSoftWrapsQuiet() {
    myUseSoftWraps = Boolean.TRUE;
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
