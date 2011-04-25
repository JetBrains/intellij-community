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
package com.intellij.usages;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ComputableIcon;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author max
 */
public class UsageInfo2UsageAdapter implements UsageInModule,
                                               UsageInLibrary, UsageInFile, PsiElementUsage,
                                               MergeableUsage, Comparable<UsageInfo2UsageAdapter>,
                                               RenameableUsage, TypeSafeDataProvider, UsagePresentation {
  public static final NotNullFunction<UsageInfo, Usage> CONVERTER = new NotNullFunction<UsageInfo, Usage>() {
    @NotNull
    public Usage fun(UsageInfo usageInfo) {
      return new UsageInfo2UsageAdapter(usageInfo);
    }
  };

  private final UsageInfo myUsageInfo;
  private final List<UsageInfo> myMergedUsageInfos = new SmartList<UsageInfo>(); // contains all merged infos, including myUsageInfo
  private int myLineNumber;
  private int myOffset = -1;
  protected ComputableIcon myIcon;
  private String myTooltipText;
  private Reference<TextChunk[]> myTextChunks; // allow to be gced and recreated on-demand because it requires a lot of memory

  public UsageInfo2UsageAdapter(@NotNull final UsageInfo usageInfo) {
    myUsageInfo = usageInfo;
    myMergedUsageInfos.add(usageInfo);

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        PsiElement element = getElement();
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(element.getContainingFile());
        int startOffset = myUsageInfo.getNavigationOffset();

        if (document != null) {
          myLineNumber = getLineNumber(document, startOffset);
          initChunks();
        }
        else {  // element over light virtual file
          TextChunk[] chunks = {
            new TextChunk(new TextAttributes(), element.getText())
          };
          myTextChunks = new SoftReference<TextChunk[]>(chunks);
          myOffset = element.getTextOffset();
        }

        if (element instanceof PsiFile) {
          myIcon = null;
        }
        else {
          myIcon = new ComputableIcon(new Computable<Icon>() {
            @Override
            public Icon compute() {
              PsiElement psiElement = getElement();
              return psiElement != null && psiElement.isValid() ? psiElement.getIcon(0) : null;
            }
          });
        }

        myTooltipText = usageInfo.getTooltipText();
      }
    });
    myModificationStamp = getCurrentModificationStamp();
  }

  private static int getLineNumber(final Document document, final int startOffset) {
    if (document.getTextLength() == 0) return 0;
    if (startOffset >= document.getTextLength()) return document.getLineCount();
    return document.getLineNumber(startOffset);
  }

  private TextChunk[] initChunks() {
    PsiFile file = getPsiFile();
    TextChunk[] chunks = file == null ? TextChunk.EMPTY_ARRAY : ChunkExtractor.extractChunks(file, this);
    myTextChunks = new SoftReference<TextChunk[]>(chunks);
    return chunks;
  }

  @NotNull
  public UsagePresentation getPresentation() {
    return this;
  }

  public boolean isValid() {
    PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    for (UsageInfo usageInfo : myMergedUsageInfos) {
      if (usageInfo.getSegment() == null) return false;
    }
    return true;
  }

  public boolean isReadOnly() {
    return isValid() && !getElement().isWritable();
  }

  @Nullable
  public FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    if (!(editor instanceof TextEditor)) return null;

    return new TextEditorLocation(getUsageInfo().getSegment().getStartOffset(), (TextEditor)editor);
  }

  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(true);
    Segment marker = getFirstSegment();
    editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
  }

  public void highlightInEditor() {
    if (!isValid()) return;

    Segment marker = getFirstSegment();
    SelectInEditorManager.getInstance(getProject()).selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
  }

  private Segment getFirstSegment() {
    return getUsageInfo().getSegment();
  }

  // must iterate in start offset order
  public boolean processRangeMarkers(Processor<Segment> processor) {
    for (UsageInfo usageInfo : myMergedUsageInfos) {
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


  public void navigate(boolean focus) {
    if (canNavigate()) {
      openTextEditor(focus);
    }
  }

  public Editor openTextEditor(boolean focus) {
    return FileEditorManager.getInstance(getProject()).openTextEditor(getDescriptor(), focus);
  }

  public boolean canNavigate() {
    VirtualFile file = getFile();
    return file != null && file.isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Nullable
  private OpenFileDescriptor getDescriptor() {
    VirtualFile file = getFile();
    if(file == null) return null;
    int offset = getNavigationOffset();
    return new OpenFileDescriptor(getProject(), file, offset);
  }

  int getNavigationOffset() {
    Document document = getDocument();
    if (document == null) return -1;
    int offset = getUsageInfo().getNavigationOffset();
    if (offset == -1) offset = myOffset;
    if (offset >= document.getTextLength()) {
      int line = Math.max(0, Math.min(myLineNumber, document.getLineCount() - 1));
      offset = document.getLineStartOffset(line);
    }
    return offset;
  }

  private Project getProject() {
    return getUsageInfo().getProject();
  }

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

  public Module getModule() {
    if (!isValid()) return null;
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    return fileIndex.getModuleForFile(virtualFile);
  }

  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    PsiFile psiFile = getPsiFile();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (psiFile instanceof PsiCompiledElement || fileIndex.isInLibrarySource(virtualFile)) {
      List<OrderEntry> orders = fileIndex.getOrderEntriesForFile(virtualFile);
      for (OrderEntry order : orders) {
        if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
          return order;
        }
      }
    }

    return null;
  }

  public VirtualFile getFile() {
    return getUsageInfo().getVirtualFile();
  }
  private PsiFile getPsiFile() {
    return getUsageInfo().getFile();
  }

  public int getLine() {
    return myLineNumber;
  }

  public boolean merge(MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo2UsageAdapter u2 = (UsageInfo2UsageAdapter)other;
    assert u2 != this;
    if (myLineNumber != u2.myLineNumber || getFile() != u2.getFile()) return false;
    myMergedUsageInfos.addAll(u2.myMergedUsageInfos);
    Collections.sort(myMergedUsageInfos, new Comparator<UsageInfo>() {
      @Override
      public int compare(UsageInfo o1, UsageInfo o2) {
        return o1.getNavigationOffset() - o2.getNavigationOffset();
      }
    });
    initChunks();
    return true;
  }

  public void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myMergedUsageInfos.isEmpty()) {
      myMergedUsageInfos.clear();
      myMergedUsageInfos.add(myUsageInfo);
      initChunks();
    }
  }

  public final PsiElement getElement() {
    return getUsageInfo().getElement();
  }

  public PsiReference getReference() {
    return getElement().getReference();
  }

  public boolean isNonCodeUsage() {
    return getUsageInfo().isNonCodeUsage;
  }

  public UsageInfo getUsageInfo() {
    return myUsageInfo;
  }

  // by start offset
  public int compareTo(final UsageInfo2UsageAdapter o) {
    VirtualFile containingFile = getFile();
    int shift1 = 0;
    if (containingFile instanceof VirtualFileWindow) {
      shift1 = ((VirtualFileWindow)containingFile).getDocumentWindow().injectedToHost(0);
      containingFile = ((VirtualFileWindow)containingFile).getDelegate();
    }
    VirtualFile oContainingFile = o.getFile();
    int shift2 = 0;
    if (oContainingFile instanceof VirtualFileWindow) {
      shift2 = ((VirtualFileWindow)oContainingFile).getDocumentWindow().injectedToHost(0);
      oContainingFile = ((VirtualFileWindow)oContainingFile).getDelegate();
    }
    if (containingFile == null && oContainingFile == null || !Comparing.equal(containingFile, oContainingFile)) {
      return 0;
    }
    Segment s1 = getFirstSegment();
    Segment s2 = o.getFirstSegment();
    if (s1 == null || s2 == null) return 0;
    return s1.getStartOffset() + shift1 - s2.getStartOffset() - shift2;
  }

  public void rename(String newName) throws IncorrectOperationException {
    final PsiReference reference = getUsageInfo().getReference();
    assert reference != null : this;
    reference.handleElementRename(newName);
  }

  public static UsageInfo2UsageAdapter[] convert(UsageInfo[] usageInfos) {
    UsageInfo2UsageAdapter[] result = new UsageInfo2UsageAdapter[usageInfos.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new UsageInfo2UsageAdapter(usageInfos[i]);
    }

    return result;
  }

  public void calcData(final DataKey key, final DataSink sink) {
    if (key == UsageView.USAGE_INFO_KEY) {
      sink.put(UsageView.USAGE_INFO_KEY, getUsageInfo());
    }
    if (key == UsageView.USAGE_INFO_LIST_KEY) {
      List<UsageInfo> list = getSelectedInfoList();
      sink.put(UsageView.USAGE_INFO_LIST_KEY, list);
    }
  }

  private List<UsageInfo> getSelectedInfoList() {
    return myMergedUsageInfos;
  }

  private long myModificationStamp;
  private long getCurrentModificationStamp() {
    final PsiFile containingFile = getPsiFile();
    return containingFile == null ? -1L : containingFile.getModificationStamp();
  }

  @NotNull
  public TextChunk[] getText() {
    TextChunk[] chunks = myTextChunks.get();
    final long currentModificationStamp = getCurrentModificationStamp();
    boolean isModified = currentModificationStamp != myModificationStamp;
    if (chunks == null || isValid() && isModified) {
      // the check below makes sense only for valid PsiElement
      chunks = initChunks();
      myModificationStamp = currentModificationStamp;
    }
    return chunks;
  }

  @NotNull
  public String getPlainText() {
    //if (myRangeMarkers.isEmpty()) { // element over light virtual file
    //  return myTextChunks[0].getText();
    //}

    int startOffset = getNavigationOffset();
    final PsiElement element = getElement();
    if (element != null && startOffset != -1) {
      final Document document = getDocument();
      if (document != null) {
        int lineNumber = document.getLineNumber(startOffset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String prefixSuffix = null;

        if (lineEnd - lineStart > ChunkExtractor.MAX_LINE_TO_SHOW) {
          prefixSuffix = "...";
          lineStart = Math.max(startOffset - ChunkExtractor.OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE, lineStart);
          lineEnd = Math.min(startOffset + ChunkExtractor.OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE, lineEnd);
        }
        String s = document.getCharsSequence().subSequence(lineStart, lineEnd).toString();
        if (prefixSuffix != null) s = prefixSuffix + s + prefixSuffix;
        return s;
      }
    }
    return "";
  }

  public Icon getIcon() {
    return myIcon != null ? myIcon.getIcon() : null;
  }

  public String getTooltipText() {
    return myTooltipText;
  }
}
