// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.visualLayer.VisualFormattingLayerService;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorCoreUtil;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class SettingsImpl implements EditorSettings {
  private static final Logger LOG = Logger.getInstance(SettingsImpl.class);
  public static final String EDITOR_SHOW_SPECIAL_CHARS = "editor.show.special.chars";

  @Nullable private final EditorImpl myEditor;
  @Nullable private Supplier<? extends Language> myLanguageSupplier;
  private Boolean myIsCamelWords;

  // This group of settings does not have UI
  private final SoftWrapAppliancePlaces mySoftWrapAppliancePlace;
  private int                     myAdditionalLinesCount          = Registry.intValue("editor.virtual.lines", 5);
  private int                     myAdditionalColumnsCount        = 3;
  private int                     myLineCursorWidth               = EditorUtil.getDefaultCaretWidth();
  private boolean                 myLineMarkerAreaShown           = true;
  private boolean                 myAllowSingleLogicalLineFolding;
  private boolean myAutoCodeFoldingEnabled = true;

  // These comes from CodeStyleSettings
  private Integer myTabSize;
  private Integer myCachedTabSize;
  private Boolean myUseTabCharacter;
  private final Object myTabSizeLock = new Object();

  // These comes from EditorSettingsExternalizable defaults.
  private Boolean myIsVirtualSpace;
  private Boolean myIsCaretInsideTabs;
  private Boolean myIsCaretBlinking;
  private Integer myCaretBlinkingPeriod;
  private Boolean myIsRightMarginShown;
  private Integer myRightMargin;
  private Boolean myAreLineNumbersShown;
  private Boolean myGutterIconsShown;
  private Boolean myIsFoldingOutlineShown;
  private Boolean myIsSmartHome;
  private Boolean myIsBlockCursor;
  private Boolean myCaretRowShown;
  private Boolean myIsWhitespacesShown;
  private Boolean myIsLeadingWhitespacesShown;
  private Boolean myIsInnerWhitespacesShown;
  private Boolean myIsTrailingWhitespacesShown;
  private Boolean myIndentGuidesShown;
  private Boolean myIsAnimatedScrolling;
  private Boolean myIsAdditionalPageAtBottom;
  private Boolean myIsDndEnabled;
  private Boolean myIsWheelFontChangeEnabled;
  private Boolean myIsMouseClickSelectionHonorsCamelWords;
  private Boolean myIsRenameVariablesInplace;
  private Boolean myIsRefrainFromScrolling;
  private Boolean myUseSoftWraps;
  private Boolean myUseCustomSoftWrapIndent;
  private Integer myCustomSoftWrapIndent;
  private Boolean myRenamePreselect;
  private Boolean myWrapWhenTypingReachesRightMargin;
  private Boolean myShowIntentionBulb;
  private Boolean myShowingSpecialCharacters;
  private Boolean myShowVisualFormattingLayer;

  private List<Integer> mySoftMargins;

  public SettingsImpl() {
    this(null, null);
  }

  SettingsImpl(@Nullable EditorImpl editor, @Nullable EditorKind kind) {
    myEditor = editor;
    if (EditorKind.CONSOLE.equals(kind)) {
      mySoftWrapAppliancePlace = SoftWrapAppliancePlaces.CONSOLE;
    }
    else if (EditorKind.PREVIEW.equals(kind)) {
      mySoftWrapAppliancePlace = SoftWrapAppliancePlaces.PREVIEW;
    }
    else {
      mySoftWrapAppliancePlace = SoftWrapAppliancePlaces.MAIN_EDITOR;
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
    if (myRightMargin != null) return myRightMargin.intValue();
    return myEditor != null
           ? CodeStyle.getSettings(myEditor).getRightMargin(getLanguage())
           : CodeStyle.getProjectOrDefaultSettings(project).getRightMargin(getLanguage());
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin(Project project) {
    if (myWrapWhenTypingReachesRightMargin != null) return myWrapWhenTypingReachesRightMargin.booleanValue();
    return myEditor == null ?
           CodeStyle.getDefaultSettings().isWrapOnTyping(getLanguage()) :
           CodeStyle.getSettings(myEditor).isWrapOnTyping(getLanguage());
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

  @NotNull
  @Override
  public List<Integer> getSoftMargins() {
    if (mySoftMargins != null) return mySoftMargins;
    return
      myEditor == null ?
      CodeStyle.getDefaultSettings().getSoftMargins(getLanguage()) :
      CodeStyle.getSettings(myEditor).getSoftMargins(getLanguage());
  }

  @Override
  public void setSoftMargins(@Nullable List<Integer> softMargins) {
    if (Objects.equals(mySoftMargins, softMargins)) return;
    mySoftMargins = softMargins != null ? new ArrayList<>(softMargins) : null;
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
    if (myUseTabCharacter != null) return myUseTabCharacter.booleanValue();
    PsiFile file = getPsiFile(project);
    return file != null
           ? CodeStyle.getIndentOptions(file).USE_TAB_CHARACTER
           : CodeStyle.getProjectOrDefaultSettings(project).getIndentOptions(null).USE_TAB_CHARACTER;
  }

  @Override
  public void setUseTabCharacter(boolean val) {
    final Boolean newValue = val ? Boolean.TRUE : Boolean.FALSE;
    if (newValue.equals(myUseTabCharacter)) return;
    myUseTabCharacter = newValue;
    fireEditorRefresh();
  }

  /**
   * @deprecated use {@link EditorKind}
   */
  @Deprecated
  public SoftWrapAppliancePlaces getSoftWrapAppliancePlace() {
    return mySoftWrapAppliancePlace;
  }

  public void reinitSettings() {
    synchronized (myTabSizeLock) {
      myCachedTabSize = null;
      reinitDocumentIndentOptions();
    }
  }

  private void reinitDocumentIndentOptions() {
    if (myEditor == null || myEditor.isViewer()) return;
    final Project project = myEditor.getProject();
    final DocumentEx document = myEditor.getDocument();

    if (project == null || project.isDisposed()) return;

    final PsiDocumentManager psiManager = PsiDocumentManager.getInstance(project);
    final PsiFile file = psiManager.getPsiFile(document);
    if (file == null) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("reinitDocumentIndentOptions, file " + file.getName());
    }

    CodeStyle.updateDocumentIndentOptions(project, document);
  }

  @Override
  public int getTabSize(Project project) {
    synchronized (myTabSizeLock) { // getTabSize can be called from a background thread (e.g. from IndentsPass)
      if (myTabSize != null) return myTabSize.intValue();
      if (myCachedTabSize == null) {
        int tabSize;
        try {
          if (project == null || project.isDisposed()) {
            tabSize = CodeStyle.getDefaultSettings().getTabSize(null);
          }
          else {
            PsiFile file = getPsiFile(project);
            if (myEditor != null && myEditor.isViewer()) {
              FileType fileType = file != null ? file.getFileType() : null;
              tabSize = CodeStyle.getSettings(project).getIndentOptions(fileType).TAB_SIZE;
            }
            else {
              tabSize = file != null ?
                        CodeStyle.getIndentOptions(file).TAB_SIZE :
                        CodeStyle.getSettings(project).getTabSize(null);
            }
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error("Error determining tab size", e);
          tabSize = new CommonCodeStyleSettings.IndentOptions().TAB_SIZE;
        }
        myCachedTabSize = Integer.valueOf(Math.max(1, tabSize));
      }
      return myCachedTabSize;
    }
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
    synchronized (myTabSizeLock) {
      if (newValue.equals(myTabSize)) return;
      myTabSize = newValue;
    }
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

    if (myEditor != null) {
      myEditor.updateCaretCursor();
      myEditor.getContentComponent().repaint();
    }
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
    return !EditorCoreUtil.isTrueSmoothScrollingEnabled() && // uses its own interpolation
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
    if (myUseSoftWraps != null) return myUseSoftWraps.booleanValue();

    boolean softWrapsEnabled = EditorSettingsExternalizable.getInstance().isUseSoftWraps(mySoftWrapAppliancePlace);
    if (!softWrapsEnabled || mySoftWrapAppliancePlace != SoftWrapAppliancePlaces.MAIN_EDITOR || myEditor == null) return softWrapsEnabled;

    String masks = EditorSettingsExternalizable.getInstance().getSoftWrapFileMasks();
    if (masks.trim().equals("*")) return true;

    VirtualFile file = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
    return file != null && fileNameMatches(file.getName(), masks);
  }

  private static boolean fileNameMatches(@NotNull String fileName, @NotNull String globPatterns) {
    for (String p : globPatterns.split(";")) {
      String pTrimmed = p.trim();
      if (!pTrimmed.isEmpty() && PatternUtil.fromMask(pTrimmed).matcher(fileName).matches()) return true;
    }
    return false;
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
    return EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown();
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

  @Nullable
  public Language getLanguage() {
    if (myLanguageSupplier != null) {
      return myLanguageSupplier.get();
    }
    return null;
  }

  @Override
  public void setLanguageSupplier(@Nullable Supplier<? extends Language> languageSupplier) {
    myLanguageSupplier = languageSupplier;
  }

  @Override
  public boolean isShowingSpecialChars() {
    return myShowingSpecialCharacters == null ? AdvancedSettings.getBoolean(EDITOR_SHOW_SPECIAL_CHARS) : myShowingSpecialCharacters;
  }

  @Override
  public void setShowingSpecialChars(boolean value) {
    boolean oldState = isShowingSpecialChars();
    myShowingSpecialCharacters = value;
    boolean newState = isShowingSpecialChars();
    if (newState != oldState) {
      fireEditorRefresh();
    }
  }

  @Override
  @Nullable
  public Boolean isShowVisualFormattingLayer() {
    return myShowVisualFormattingLayer;
  }

  @Override
  public void setShowVisualFormattingLayer(@Nullable Boolean showVisualFormattingLayer) {
    myShowVisualFormattingLayer = showVisualFormattingLayer;
  }

}
