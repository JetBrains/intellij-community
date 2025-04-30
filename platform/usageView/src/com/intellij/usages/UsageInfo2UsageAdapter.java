// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.ide.TypePresentationService;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.impl.UsageViewStatisticsCollector;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.rules.*;
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UsageInfo2UsageAdapter implements UsageInModule, UsageInfoAdapter,
                                               UsageInLibrary, UsageInFile, PsiElementUsage,
                                               MergeableUsage,
                                               RenameableUsage, UiCompatibleDataProvider, UsagePresentation {
  public static final NotNullFunction<UsageInfo, Usage> CONVERTER = UsageInfo2UsageAdapter::new;
  private static final Comparator<UsageInfo> BY_NAVIGATION_OFFSET = Comparator.comparingInt(UsageInfo::getNavigationOffset);
  @SuppressWarnings("StaticNonFinalField")
  public static boolean ourAutomaticallyCalculatePresentationInTests = true;

  private final @NotNull UsageInfo myUsageInfo;
  private @NotNull Object myMergedUsageInfos; // contains all merged infos, including myUsageInfo. Either UsageInfo or UsageInfo[]
  private @Nullable SmartPsiFileRange myMergedNavigationRange;
  private final int myLineNumber;
  private final int myOffset;
  private volatile UsageNodePresentation myCachedPresentation;
  private final @Nullable VirtualFile myVirtualFile;
  private final @Nullable SmartPsiFileRange myNavigationRange;
  private volatile UsageType myUsageType;

  private static class ComputedData {
    public final int offset;
    public final int lineNumber;
    public final VirtualFile virtualFile;
    public final @Nullable SmartPsiFileRange navigationRange;
    public final long modificationStamp;

    private ComputedData(int offset, int lineNumber, VirtualFile virtualFile, @Nullable SmartPsiFileRange navigationRange, long stamp) {
      this.offset = offset;
      this.lineNumber = lineNumber;
      this.virtualFile = virtualFile;
      this.navigationRange = navigationRange;
      this.modificationStamp = stamp;
    }
  }

  private static @Nullable SmartPsiFileRange possiblySmart(@Nullable PsiFile psiFile, @Nullable Segment segment) {
    if (segment == null) return null;
    if (psiFile != null && segment instanceof TextRange range) {
      return SmartPointerManager.getInstance(psiFile.getProject()).createSmartPsiFileRangePointer(psiFile, range);
    }
    else {
      return null;
    }
  }

  public UsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo) {
    myUsageInfo = usageInfo;
    myMergedUsageInfos = usageInfo;

    ComputedData data =
    ReadAction.compute(() -> {
      VirtualFile virtualFile = usageInfo.getVirtualFile();
      PsiElement element = getElement();
      PsiFile psiFile = usageInfo.getFile();
      boolean isNullOrBinary = psiFile == null || psiFile.getFileType().isBinary();
      Document document = isNullOrBinary
                          ? null : PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);

      int offset;
      int lineNumber;
      Segment navigationRange;
      if (document == null) {
        // element over light virtual file
        offset = element == null || isNullOrBinary ? 0 : element.getTextOffset();
        navigationRange = element == null || isNullOrBinary ? null : element.getTextRange();
        lineNumber = -1;
      }
      else {
        Segment range = myUsageInfo.getNavigationRange();
        if (range == null || range.getStartOffset() == -1) {
          offset = element == null ? 0 : element.getTextOffset();
          navigationRange = element == null ? null : element.getTextRange();
          lineNumber = -1;
        }
        else {
          offset = -1;
          navigationRange = range;
          lineNumber = getLineNumber(document, range.getStartOffset());
        }
      }

      var modificationStamp = psiFile == null ? -1 : psiFile.getViewProvider().getModificationStamp();
      return new ComputedData(offset, lineNumber, virtualFile, possiblySmart(psiFile, navigationRange), modificationStamp);
    });
    myOffset = data.offset;
    myLineNumber = data.lineNumber;
    myVirtualFile = data.virtualFile;
    myNavigationRange = data.navigationRange;
    myMergedNavigationRange = data.navigationRange;
    myModificationStamp = data.modificationStamp;
  }

  @Override
  public UsageInfo @NotNull [] getMergedInfos() {
    Object infos = myMergedUsageInfos;
    return infos instanceof UsageInfo ? new UsageInfo[]{(UsageInfo)infos} : (UsageInfo[])infos;
  }

  @Override
  public @NotNull CompletableFuture<UsageInfo[]> getMergedInfosAsync() {
    return CompletableFuture.completedFuture(getMergedInfos());
  }

  private static int getLineNumber(@NotNull Document document, int startOffset) {
    if (document.getTextLength() == 0) return 0;
    if (startOffset >= document.getTextLength()) return document.getLineCount();
    return document.getLineNumber(startOffset);
  }

  private Color computeBackgroundColor() {
    VirtualFile file = getFile();
    if (file == null) {
      return null;
    }

    return EditorTabPresentationUtil.getFileBackgroundColor(getProject(), file);
  }

  protected TextChunk @NotNull [] computeText() {
    TextChunk[] chunks;
    PsiFile psiFile = getPsiFile();
    boolean isNullOrBinary = psiFile == null || psiFile.getFileType().isBinary();

    PsiElement element = getElement();
    if (element != null && isNullOrBinary) {
      EditorColorsScheme scheme = UsageTreeColorsScheme.getInstance().getScheme();
      chunks = new TextChunk[]{
        new TextChunk(scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME), clsType(element)),
        new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), " "),
        new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), clsName(element)),
      };
    }
    else {
      Document document = psiFile == null ? null : PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document == null) {
        // element over light virtual file
        if (element == null) {
          chunks = new TextChunk[]{
            new TextChunk(SimpleTextAttributes.ERROR_ATTRIBUTES.toTextAttributes(), UsageViewBundle.message("node.invalid"))};
        }
        else {
          chunks = new TextChunk[]{new TextChunk(new TextAttributes(), element.getText())};
        }
      }
      else {
        chunks = ChunkExtractor.extractChunks(psiFile, this);
      }
    }
    return chunks;
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    return this;
  }

  @Override
  public boolean isValid() {
    PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    for (UsageInfo usageInfo : getMergedInfos()) {
      if (usageInfo.isValid()) return true;
    }
    return false;
  }

  @Override
  public boolean isReadOnly() {
    PsiFile psiFile = getPsiFile();
    return psiFile == null || psiFile.isValid() && !psiFile.isWritable();
  }

  @Override
  public @Nullable FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    if (!(editor instanceof TextEditor)) return null;

    Segment segment = getUsageInfo().getSegment();
    if (segment == null) return null;
    return new TextEditorLocation(segment.getStartOffset(), (TextEditor)editor);
  }

  @Override
  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(true);
    Segment marker = getFirstSegment();
    if (marker != null) {
      editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
    }
  }

  @Override
  public void highlightInEditor() {
    if (!isValid()) return;

    Segment marker = getFirstSegment();
    if (marker != null) {
      SelectInEditorManager.getInstance(getProject()).selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
    }
  }

  private Segment getFirstSegment() {
    return getUsageInfo().getSegment();
  }

  // must iterate in start offset order
  public boolean processRangeMarkers(@NotNull Processor<? super Segment> processor) {
    for (UsageInfo usageInfo : getMergedInfos()) {
      Segment segment = usageInfo.getSegment();
      if (segment != null && !processor.process(segment)) {
        return false;
      }
    }
    return true;
  }

  public Document getDocument() {
    PsiFile file = getUsageInfo().getFile();
    if (file == null) return null;
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }


  @Override
  public void navigate(boolean focus) {
    if (canNavigate()) {
      UsageViewStatisticsCollector.logUsageNavigate(getProject(), this);
      openTextEditor(focus);
    }
  }

  public Editor openTextEditor(boolean focus) {
    OpenFileDescriptor descriptor = getDescriptor();
    if (descriptor == null) return null;

    return FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, focus);
  }

  @Override
  public boolean canNavigate() {
    VirtualFile file = getFile();
    return file != null && file.isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  private OpenFileDescriptor getDescriptor() {
    VirtualFile file = getFile();
    if(file == null) return null;
    Segment range = getNavigationRange();
    if (range != null && file instanceof VirtualFileWindow && range.getStartOffset() >= 0) {
      // have to use injectedToHost(TextRange) to calculate right offset in case of multiple shreds
      range = ((VirtualFileWindow)file).getDocumentWindow().injectedToHost(TextRange.create(range));
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return new OpenFileDescriptor(getProject(), file, range == null ? getNavigationOffset() : range.getStartOffset());
  }

  @Override
  public int getNavigationOffset() {
    Document document = getDocument();
    if (document == null) return -1;
    int offset = getStartOffset(myMergedNavigationRange);
    if (offset == -1) offset = getUsageInfo().getNavigationOffset();
    if (offset == -1) offset = myOffset;
    if (offset >= document.getTextLength()) {
      int line = Math.max(0, Math.min(myLineNumber, document.getLineCount() - 1));
      offset = document.getLineStartOffset(line);
    }
    return offset;
  }

  private static int getStartOffset(@Nullable SmartPsiFileRange smartPsiFileRange) {
    int offset = -1;
    if (smartPsiFileRange != null) {
      var range = smartPsiFileRange.getRange();
      offset = range != null ? range.getStartOffset() : -1;
    }
    return offset;
  }

  /**
   * Returns the text range of the usage relative to the start of the file.
   */
  public Segment getNavigationRange() {
    Document document = getDocument();
    if (document == null) return null;
    Segment range = myMergedNavigationRange != null ? myMergedNavigationRange.getRange() : null;
    if (range == null) range = getUsageInfo().getNavigationRange();
    if (range == null) {
      ProperTextRange rangeInElement = getUsageInfo().getRangeInElement();
      range = myOffset < 0 ? new UnfairTextRange(-1,-1) : rangeInElement == null ? TextRange.from(myOffset,1) : rangeInElement.shiftRight(myOffset);
    }
    if (range.getEndOffset() >= document.getTextLength()) {
      int line = Math.max(0, Math.min(myLineNumber, document.getLineCount() - 1));
      range = TextRange.from(document.getLineStartOffset(line),1);
    }
    return range;
  }

  private @NotNull Project getProject() {
    return getUsageInfo().getProject();
  }

  @Override
  public String toString() {
    TextChunk[] textChunks = getPresentation().getText();
    StringBuilder result = new StringBuilder();
    for (int j = 0; j < textChunks.length; j++) {
      if (j > 0) result.append("|");
      TextChunk textChunk = textChunks[j];
      result.append(textChunk);
    }

    return result.toString();
  }

  @Override
  public Module getModule() {
    if (!isValid()) return null;
    VirtualFile virtualFile = getFile();
    return virtualFile != null ? ProjectFileIndex.getInstance(getProject()).getModuleForFile(virtualFile) : null;
  }

  @Override
  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(getProject());
    if (virtualFile.getFileType().isBinary() || fileIndex.isInLibrarySource(virtualFile)) {
      List<OrderEntry> orders = fileIndex.getOrderEntriesForFile(virtualFile);
      for (OrderEntry order : orders) {
        if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
          return order;
        }
      }
    }

    return null;
  }

  @Override
  public @NotNull List<SyntheticLibrary> getSyntheticLibraries() {
    if (!isValid()) return Collections.emptyList();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return Collections.emptyList();

    Project project = getProject();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    if (!fileIndex.isInLibrarySource(virtualFile)) return Collections.emptyList();

    VirtualFile sourcesRoot = fileIndex.getSourceRootForFile(virtualFile);
    if (sourcesRoot != null) {
      List<SyntheticLibrary> list = new ArrayList<>();
      for (AdditionalLibraryRootsProvider e : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        for (SyntheticLibrary library : e.getAdditionalProjectLibraries(project)) {
          if (library.getSourceRoots().contains(sourcesRoot)) {
            Condition<? super VirtualFile> excludeFileCondition = library.getUnitedExcludeCondition();
            if (excludeFileCondition == null || !excludeFileCondition.value(virtualFile)) {
              list.add(library);
            }
          }
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @Override
  public VirtualFile getFile() {
    return myVirtualFile;
  }
  private PsiFile getPsiFile() {
    return getUsageInfo().getFile();
  }

  @Override
  public @NotNull String getPath() {
    VirtualFile file = getFile();
    return file != null ? file.getPath() : "";
  }

  @Override
  public int getLine() {
    return myLineNumber;
  }

  @Override
  public boolean merge(@NotNull MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter u2)) return false;
    assert u2 != this;
    if (myLineNumber != u2.myLineNumber || !Comparing.equal(getFile(), u2.getFile())) return false;
    UsageInfo[] merged = ArrayUtil.mergeArrays(getMergedInfos(), u2.getMergedInfos());
    myMergedUsageInfos = merged.length == 1 ? merged[0] : merged;
    Arrays.sort(getMergedInfos(), BY_NAVIGATION_OFFSET);
    myMergedNavigationRange = merge(myMergedNavigationRange, u2.myMergedNavigationRange);

    // Invalidate cached presentation, so it'll be updated later
    // Do not reset it to still have something to present
    myModificationStamp = Long.MIN_VALUE;

    return true;
  }

  private static @Nullable SmartPsiFileRange merge(
    @Nullable SmartPsiFileRange sr1,
    @Nullable SmartPsiFileRange sr2
  ) {
    if (sr1 == null) return sr2;
    if (sr2 == null) return sr1;
    var r1 = sr1.getRange();
    var r2 = sr2.getRange();
    if (r1 == null) return sr2;
    if (r2 == null) return sr1;
    var r1start = r1.getStartOffset();
    var r2start = r2.getStartOffset();
    if (r1start != r2start) {
      return r1start <= r2start ? sr1 : sr2;
    }
    else {
      var r1end = r1.getEndOffset();
      var r2end = r2.getEndOffset();
      return r1end <= r2end ? sr1 : sr2;
    }
  }

  @Override
  public void reset() {
    ThreadingAssertions.assertEventDispatchThread();
    myMergedUsageInfos = myUsageInfo;
    myMergedNavigationRange = myNavigationRange;
    resetCachedPresentation();
  }

  protected final void resetCachedPresentation() {
    // Invalidate cached presentation, so it'll be updated later
    // Do not clear it to still have something to present
    myModificationStamp = Long.MIN_VALUE;
  }

  @Override
  public final @Nullable PsiElement getElement() {
    return getUsageInfo().getElement();
  }

  @Override
  public boolean isNonCodeUsage() {
    return getUsageInfo().isNonCodeUsage;
  }

  public @NotNull UsageInfo getUsageInfo() {
    return myUsageInfo;
  }

  // by start offset
  public final int compareTo(@NotNull UsageInfo2UsageAdapter o) {
    int byPath = VfsUtilCore.compareByPath(myVirtualFile, o.myVirtualFile);
    if (byPath != 0) {
      return byPath;
    }

    int offset = getStartOffset(myMergedNavigationRange);
    int other = getStartOffset(o.myMergedNavigationRange);

    return Integer.compare(offset, other);
  }

  @Override
  public void rename(@NotNull String newName) throws IncorrectOperationException {
    PsiReference reference = getUsageInfo().getReference();
    assert reference != null : this;
    reference.handleElementRename(newName);
  }

  public static UsageInfo2UsageAdapter @NotNull [] convert(UsageInfo @NotNull [] usageInfos) {
    UsageInfo2UsageAdapter[] result = new UsageInfo2UsageAdapter[usageInfos.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new UsageInfo2UsageAdapter(usageInfos[i]);
    }

    return result;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(UsageView.USAGE_INFO_KEY, getUsageInfo());
    sink.set(UsageView.USAGE_INFO_LIST_KEY, Arrays.asList(getMergedInfos()));
  }

  private long myModificationStamp;
  private long getCurrentModificationStamp() {
    PsiFile containingFile = getPsiFile();
    return containingFile == null ? -1L : containingFile.getViewProvider().getModificationStamp();
  }

  @Override
  public TextChunk @NotNull [] getText() {
    return getNotNullCachedPresentation().getText();
  }

  @ApiStatus.Internal
  @Override
  public final @Nullable UsageNodePresentation getCachedPresentation() {
    return myCachedPresentation;
  }

  @ApiStatus.Internal
  @Override
  public final void updateCachedPresentation() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }

    UsageNodePresentation cachedPresentation = getCachedPresentation();
    long currentModificationStamp = getCurrentModificationStamp();
    boolean isModified = currentModificationStamp != myModificationStamp;
    if (cachedPresentation == null || isModified && isValid()) {
      myCachedPresentation = new UsageNodePresentation(computeIcon(), computeText(), computeBackgroundColor());
      myModificationStamp = currentModificationStamp;
    }
  }

  private @NotNull UsageNodePresentation getNotNullCachedPresentation() {
    // Presentation is expected to be always externally updated by calling updateCachedPresentation
    // Here we just return cached result because it must be always available for painting or speed search
    UsageNodePresentation cachedPresentation = getCachedPresentation();
    return cachedPresentation != null ? cachedPresentation : UsageNodePresentation.empty();
  }

  private static @NotNull String clsType(@NotNull PsiElement psiElement) {
    String type = LanguageFindUsages.getType(psiElement);
    if (!type.isEmpty()) return type;
    return ObjectUtils.notNull(TypePresentationService.getService().getTypePresentableName(psiElement.getClass()), "");
  }
  private static @NotNull String clsName(@NotNull PsiElement psiElement) {
    String name = LanguageFindUsages.getNodeText(psiElement, false);
    if (!name.isEmpty()) return name;
    return ObjectUtils.notNull(psiElement instanceof PsiNamedElement ? ((PsiNamedElement)psiElement).getName() : null, "");
  }

  @Override
  public @NotNull String getPlainText() {
    PsiElement element = getElement();
    PsiFile psiFile = getPsiFile();
    boolean isNullOrBinary = psiFile == null || psiFile.getFileType().isBinary();
    if (element != null && isNullOrBinary) {
      return clsType(element) + " " + clsName(element);
    }
    int startOffset;
    if (element != null && (startOffset = getNavigationOffset()) != -1) {
      Document document = getDocument();
      if (document != null) {
        int lineNumber = document.getLineNumber(startOffset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String prefixSuffix = null;

        if (lineEnd - lineStart > ChunkExtractor.MAX_LINE_LENGTH_TO_SHOW) {
          prefixSuffix = "...";
          lineStart = Math.max(startOffset - ChunkExtractor.OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE, lineStart);
          lineEnd = Math.min(startOffset + ChunkExtractor.OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE, lineEnd);
        }
        String s = document.getCharsSequence().subSequence(lineStart, lineEnd).toString();
        if (prefixSuffix != null) s = prefixSuffix + s + prefixSuffix;
        return s;
      }
    }
    return UsageViewBundle.message("node.invalid");
  }

  @Override
  public @Nullable Color getBackgroundColor() {
    return getNotNullCachedPresentation().getBackgroundColor();
  }

  @Override
  public Icon getIcon() {
    return getNotNullCachedPresentation().getIcon();
  }

  protected @Nullable Icon computeIcon() {
    Icon icon = myUsageInfo.getIcon();
    if (icon != null) {
      return icon;
    }
    PsiElement psiElement = getElement();
    return psiElement != null && psiElement.isValid() && !isFindInPathUsage(psiElement) ? psiElement.getIcon(0) : null;
  }

  private boolean isFindInPathUsage(PsiElement psiElement) {
    return psiElement instanceof PsiFile && getUsageInfo().getPsiFileRange() != null;
  }

  @Override
  public String getTooltipText() {
    return myUsageInfo.getTooltipText();
  }

  public @Nullable UsageType getUsageType() {
    UsageType usageType = myUsageType;
    if (usageType == null) {
      usageType = computeUsageType();
      if (usageType == null) {
        usageType = UsageType.UNCLASSIFIED;
      }
      myUsageType = usageType;
    }
    return usageType;
  }

  private @Nullable UsageType computeUsageType() {
    PsiFile file = getPsiFile();
    if (file == null) {
      return null;
    }
    Segment segment = getFirstSegment();
    if (segment == null) {
      return null;
    }
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    if (document == null) {
      return null;
    }
    return ChunkExtractor.getExtractor(file).deriveUsageTypeFromHighlighting(
      document.getCharsSequence(), segment.getStartOffset(), segment.getEndOffset()
    );
  }

  @Override
  public @Nullable Class<? extends PsiReference> getReferenceClass() {
    return myUsageInfo.getReferenceClass();
  }

  @TestOnly
  @ApiStatus.Internal
  public static void disableAutomaticPresentationCalculationInTests(Runnable block) {
    boolean old = ourAutomaticallyCalculatePresentationInTests;
    ourAutomaticallyCalculatePresentationInTests = false;
    try {
      block.run();
    }
    finally {
      ourAutomaticallyCalculatePresentationInTests = old;
    }
  }
}
