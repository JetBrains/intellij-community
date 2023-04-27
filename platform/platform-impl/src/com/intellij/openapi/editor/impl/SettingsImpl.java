// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.PatternUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class SettingsImpl implements EditorSettings {
  private static final Logger LOG = Logger.getInstance(SettingsImpl.class);
  public static final String EDITOR_SHOW_SPECIAL_CHARS = "editor.show.special.chars";

  @Nullable private final EditorImpl myEditor;
  @Nullable private Supplier<? extends Language> myLanguageSupplier;
  private Boolean myIsCamelWords;

  // This group of settings does not have UI
  private final SoftWrapAppliancePlaces mySoftWrapAppliancePlace;
  private int myAdditionalLinesCount = Registry.intValue("editor.virtual.lines", 5);
  private int myAdditionalColumnsCount = 3;
  private int myLineCursorWidth = EditorUtil.getDefaultCaretWidth();
  private boolean myLineMarkerAreaShown = true;
  private boolean myAllowSingleLogicalLineFolding;
  private boolean myAutoCodeFoldingEnabled = true;

  // These come from CodeStyleSettings.
  private Boolean myUseTabCharacter;

  // These come from EditorSettingsExternalizable defaults.
  private Boolean myIsVirtualSpace;
  private Boolean myIsCaretInsideTabs;
  private Boolean myIsCaretBlinking;
  private Integer myCaretBlinkingPeriod;
  private Boolean myIsRightMarginShown;

  private Integer myVerticalScrollOffset;
  private Integer myVerticalScrollJump;
  private Integer myHorizontalScrollOffset;
  private Integer myHorizontalScrollJump;

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
  private Boolean myIsSelectionWhitespacesShown;
  private Boolean myIndentGuidesShown;
  private Boolean myIsAnimatedScrolling;
  private Boolean myIsAdditionalPageAtBottom;
  private Boolean myIsDndEnabled;
  private Boolean myIsWheelFontChangeEnabled;
  private Boolean myIsMouseClickSelectionHonorsCamelWords;
  private Boolean myIsRenameVariablesInplace;
  private Boolean myIsRefrainFromScrolling;
  private Boolean myUseSoftWraps;
  private boolean myPaintSoftWraps = true;
  private Boolean myUseCustomSoftWrapIndent;
  private Integer myCustomSoftWrapIndent;
  private Boolean myRenamePreselect;
  private Boolean myWrapWhenTypingReachesRightMargin;
  private Boolean myShowIntentionBulb;
  private Boolean myShowingSpecialCharacters;

  private final List<CacheableBackgroundComputable<?>> myComputableSettings = new ArrayList<>();

  private final ExecutorService myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("EditorSettings", 3);

  private final CacheableBackgroundComputable<List<Integer>> mySoftMargins = new CacheableBackgroundComputable<>(Collections.emptyList()) {
    @Override
    protected List<Integer> computeValue(@Nullable Project project) {
      if(myEditor == null) return Collections.emptyList();
      return CodeStyle.getSettings(myEditor).getSoftMargins(getLanguage());
    }
  };

  private final CacheableBackgroundComputable<Integer> myRightMargin =
    new CacheableBackgroundComputable<>(CodeStyleSettings.getDefaults().RIGHT_MARGIN) {
      @Override
      protected Integer computeValue(@Nullable Project project) {
        return myEditor != null
               ? CodeStyle.getSettings(myEditor).getRightMargin(getLanguage())
               : CodeStyle.getProjectOrDefaultSettings(project).getRightMargin(getLanguage());
      }
    };

  private final CacheableBackgroundComputable<Integer> myTabSize =
    new CacheableBackgroundComputable<>(CodeStyleSettings.getDefaults().getIndentOptions().TAB_SIZE) {
      @Override
      protected Integer computeValue(@Nullable Project project) {
        int tabSize;
        if (project == null) {
          tabSize = CodeStyle.getDefaultSettings().getTabSize(null);
        }
        else {
          VirtualFile file = getVirtualFile();
          if (myEditor != null && myEditor.isViewer()) {
            FileType fileType = file != null ? file.getFileType() : null;
            tabSize = CodeStyle.getSettings(project).getIndentOptions(fileType).TAB_SIZE;
          }
          else {
            tabSize = file != null ?
                      CodeStyle.getIndentOptions(project, file).TAB_SIZE :
                      CodeStyle.getSettings(project).getTabSize(null);
          }
        }
        return Integer.valueOf(Math.max(1, tabSize));
      }
    };

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
    if (myIsRightMarginShown != null) {
      return myIsRightMarginShown.booleanValue();
    }
    if (myEditor != null && getRightMargin(myEditor.getProject()) == CodeStyleConstraints.MAX_RIGHT_MARGIN) {
      return false;
    }
    return EditorSettingsExternalizable.getInstance().isRightMarginShown();
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
  public boolean isSelectionWhitespaceShown() {
    return myIsSelectionWhitespacesShown != null
           ? myIsSelectionWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSelectionWhitespacesShown();
  }

  @Override
  public void setSelectionWhitespaceShown(boolean val) {
    myIsSelectionWhitespacesShown = Boolean.valueOf(val);
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
    return myRightMargin.getValue(project).intValue();
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
    myRightMargin.setValue(rightMargin);
  }

  @NotNull
  @Override
  public List<Integer> getSoftMargins() {
    return mySoftMargins.getValue(null);
  }

  @Override
  public void setSoftMargins(@Nullable List<Integer> softMargins) {
    mySoftMargins.setValue(softMargins != null ? new ArrayList<>(softMargins) : null);
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
    VirtualFile file = getVirtualFile();
    return file != null
           ? CodeStyle.getIndentOptions(project, file).USE_TAB_CHARACTER
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
    myComputableSettings.forEach(CacheableBackgroundComputable::resetCache);
    reinitDocumentIndentOptions();
  }

  private void reinitDocumentIndentOptions() {
    if (myEditor == null || myEditor.isViewer()) return;
    final Project project = myEditor.getProject();
    final DocumentEx document = myEditor.getDocument();

    if (project == null || project.isDisposed()) return;

    VirtualFile file = getVirtualFile();
    if (file == null) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("reinitDocumentIndentOptions, file " + file.getName());
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      computeIndentOptions(project, file).associateWithDocument(document);
    }
    else {
      ReadAction.nonBlocking(
        () -> computeIndentOptions(project, file)
      ).expireWhen(
        () -> myEditor.isDisposed() || project.isDisposed()
      ).finishOnUiThread(
        ModalityState.any(),
        result -> result.associateWithDocument(document)
      ).submit(myExecutor);
    }
  }

  private static @NotNull CommonCodeStyleSettings.IndentOptions computeIndentOptions(@NotNull Project project, @NotNull VirtualFile file) {
    return CodeStyle
      .getSettings(project, file)
      .getIndentOptionsByFile(project, file, null, true, null);
  }

  @Override
  public int getTabSize(Project project) {
    return myTabSize.getValue(project);
  }

  @Nullable
  private VirtualFile getVirtualFile() {
    VirtualFile file = null;
    if (myEditor != null) {
      file = myEditor.getVirtualFile();
      if (file == null) {
        Document document = myEditor.getDocument();
        file = FileDocumentManager.getInstance().getFile(document);
      }
    }
    return file;
  }

  @Override
  public void setTabSize(int tabSize) {
    myTabSize.setValue(tabSize);
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
  public int getVerticalScrollOffset() {
    return myVerticalScrollOffset == null ? EditorSettingsExternalizable.getInstance().getVerticalScrollOffset() : myVerticalScrollOffset;
  }

  @Override
  public void setVerticalScrollOffset(int val) {
    final Integer newValue = Integer.valueOf(val);
    if (Objects.equals(myVerticalScrollOffset, newValue)) return;
    myVerticalScrollOffset = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getVerticalScrollJump() {
    return myVerticalScrollJump == null ? EditorSettingsExternalizable.getInstance().getVerticalScrollJump() : myVerticalScrollJump;
  }

  @Override
  public void setVerticalScrollJump(int val) {
    myVerticalScrollJump = val;
  }

  @Override
  public int getHorizontalScrollOffset() {
    return myHorizontalScrollOffset == null ? EditorSettingsExternalizable.getInstance().getHorizontalScrollOffset() : myHorizontalScrollOffset;
  }

  @Override
  public void setHorizontalScrollOffset(int val) {
    final Integer newValue = Integer.valueOf(val);
    if (Objects.equals(myHorizontalScrollOffset, newValue)) return;
    myHorizontalScrollOffset = newValue;
    fireEditorRefresh();
  }

  @Override
  public int getHorizontalScrollJump() {
    return myHorizontalScrollJump == null ? EditorSettingsExternalizable.getInstance().getHorizontalScrollJump() : myHorizontalScrollJump;
  }

  @Override
  public void setHorizontalScrollJump(int val) {
    myHorizontalScrollJump = val;
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
    myIsRenameVariablesInplace = val ? Boolean.TRUE : Boolean.FALSE;
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
  public boolean isPaintSoftWraps() {
    return myPaintSoftWraps;
  }

  @Override
  public void setPaintSoftWraps(boolean val) {
    myPaintSoftWraps = val;
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
    fireEditorRefresh(true);
  }

  private void fireEditorRefresh(boolean reinitSettings) {
    if (myEditor != null) {
      myEditor.reinitSettings(true, reinitSettings);
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
  public boolean isInsertParenthesesAutomatically() {
    return EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically();
  }

  private abstract class CacheableBackgroundComputable<T> {

    private @Nullable T myOverwrittenValue;
    private @Nullable T myCachedValue;
    private @NotNull T myDefaultValue;


    private final AtomicReference<NonBlockingReadAction<T>> myCurrentReadActionRef = new AtomicReference<>();

    private final static Object VALUE_LOCK = new Object();

    private CacheableBackgroundComputable(@NotNull T defaultValue) {
      myComputableSettings.add(this);
      myDefaultValue = defaultValue;
    }

    private void setValue(@Nullable T overwrittenValue) {
      synchronized (VALUE_LOCK) {
        if (Objects.equals(myOverwrittenValue, overwrittenValue)) return;
        myOverwrittenValue = overwrittenValue;
      }
      fireEditorRefresh();
    }

    private @NotNull T getValue(@Nullable Project project) {
      synchronized (VALUE_LOCK) {
        if (myOverwrittenValue != null) return myOverwrittenValue;
        if (myCachedValue != null) return myCachedValue;
        return getDefaultAndCompute(project);
      }
    }

    private void resetCache() {
      synchronized (VALUE_LOCK) {
        myCachedValue = null;
      }
    }

    protected abstract T computeValue(@Nullable Project project);

    private @NotNull T getDefaultAndCompute(@Nullable Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          return computeValue(project);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      else {
        if (myCurrentReadActionRef.get() == null) {
          NonBlockingReadAction<T> readAction = ReadAction
            .nonBlocking(() -> computeValue(project))
            .finishOnUiThread(
              ModalityState.any(),
              result -> {
                myCurrentReadActionRef.set(null);
                synchronized (VALUE_LOCK) {
                  myCachedValue = result;
                  myDefaultValue = result;
                }
                fireEditorRefresh(false);
              }
            )
            .expireWhen(() -> myEditor != null && myEditor.isDisposed() || project != null && project.isDisposed());
          if (myCurrentReadActionRef.compareAndSet(null, readAction)) {
            readAction.submit(myExecutor);
          }
        }
      }
      return myDefaultValue;
    }
  }
}
