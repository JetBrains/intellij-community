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
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.ComputableIcon;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.rules.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UsageInfo2UsageAdapter implements UsageInModule, UsageInLibrary, UsageInFile, PsiElementUsage, MergeableUsage, Comparable<UsageInfo2UsageAdapter>, RenameableUsage,
                                               TypeSafeDataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.UsageInfo2UsageAdapter");

  public static final NotNullFunction<UsageInfo, Usage> CONVERTER = new NotNullFunction<UsageInfo, Usage>() {
    @NotNull
    public Usage fun(UsageInfo usageInfo) {
      return new UsageInfo2UsageAdapter(usageInfo);
    }
  };

  private final UsageInfo myUsageInfo;
  private int myLineNumber;
  private int myOffset = -1;
  protected ComputableIcon myIcon;
  private String myTooltipText;
  private List<RangeMarker> myRangeMarkers = new ArrayList<RangeMarker>();
  private TextChunk[] myTextChunks;
  private final UsagePresentation myUsagePresentation;

  public UsageInfo2UsageAdapter(final UsageInfo usageInfo) {
    myUsageInfo = usageInfo;

    myUsagePresentation = ApplicationManager.getApplication().runReadAction(new Computable<UsagePresentation>() {
      public UsagePresentation compute() {
        PsiElement element = getElement();
        PsiFile psiFile = element.getContainingFile();
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(psiFile);

        TextRange range = element.getTextRange();
        int startOffset = range.getStartOffset() + myUsageInfo.startOffset;
        int endOffset = range.getStartOffset() + myUsageInfo.endOffset;

        if (document != null) {
          myLineNumber = getLineNumber(document, startOffset);

          if (endOffset > document.getTextLength()) {
            LOG.error("Invalid usage info, psiElement:" + element + " end offset: " + endOffset + " psiFile: " + psiFile.getName());
          }

          myRangeMarkers.add(document.createRangeMarker(startOffset, endOffset));
          initChunks();
        } else {  // element over light virtual file
          myTextChunks = new TextChunk[] {
            new TextChunk(new TextAttributes(), element.getText())
          };
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

        myTooltipText = myUsageInfo.getTooltipText();

        return new MyUsagePresentation();
      }
    });
  }

  private static int getLineNumber(final Document document, final int startOffset) {
    if (document.getTextLength() == 0) return 0;
    if (startOffset >= document.getTextLength()) return document.getLineCount();
    return document.getLineNumber(startOffset);
  }

  private void initChunks() {
    myTextChunks = ChunkExtractor.extractChunks(getElement(), myRangeMarkers);
  }
  
  @NotNull
  public UsagePresentation getPresentation() {
    return myUsagePresentation;
  }

  public boolean isValid() {
    PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return false;
    }

    return markersValid();
  }

  private boolean markersValid() {
    for (RangeMarker rangeMarker : myRangeMarkers) {
      if (!rangeMarker.isValid()) {
        return false;
      }
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

    return new TextEditorLocation(myUsageInfo.startOffset + getElement().getTextRange().getStartOffset(), (TextEditor)editor);
  }

  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(true);
    RangeMarker marker = getRangeMarker();
    editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
  }

  public void highlightInEditor() {
    if (!isValid()) return;

    RangeMarker marker = getRangeMarker();
    SelectInEditorManager.getInstance(getProject()).selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
  }

  public final RangeMarker getRangeMarker() {
    return myRangeMarkers.get(0);
  }

  public List<RangeMarker> getRangeMarkers() {
    return myRangeMarkers;
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
    return getFile().isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Nullable
  private OpenFileDescriptor getDescriptor() {
    if (markersValid()) {
      return new OpenFileDescriptor(getProject(), getFile(), !myRangeMarkers.isEmpty() ? getRangeMarker().getStartOffset() : myOffset);
    }
    else if (getFile().isValid()) {
      final Document doc = FileDocumentManager.getInstance().getDocument(getFile());
      if (doc != null) {
        int line = Math.max(0, Math.min(myLineNumber, doc.getLineCount()));
        return new OpenFileDescriptor(getProject(), getFile(), line, 0);
      }
    }
    
    return null;
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
    PsiElement element = getElement();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    return fileIndex.getModuleForFile(virtualFile);
  }

  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    PsiElement element = getElement();
    PsiFile psiFile = element.getContainingFile();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (psiFile instanceof PsiCompiledElement || fileIndex.isInLibrarySource(virtualFile)) {
      List<OrderEntry> orders = fileIndex.getOrderEntriesForFile(psiFile.getVirtualFile());
      for (OrderEntry order : orders) {
        if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
          return order;
        }
      }
    }

    return null;
  }

  public VirtualFile getFile() {
    if (isValid()) {
      return getElement().getContainingFile().getVirtualFile();
    }
    else {
      return myUsageInfo.getVirtualFile();
    }
  }

  public int getLine() {
    return myLineNumber;
  }

  public boolean merge(MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo2UsageAdapter u2 = (UsageInfo2UsageAdapter)other;
    if (myLineNumber != u2.myLineNumber || getFile() != u2.getFile()) return false;
    myRangeMarkers.addAll(u2.myRangeMarkers);
    initChunks();
    return true;
  }

  public void reset() {
    if (myRangeMarkers.size() > 1) {
      RangeMarker marker = getRangeMarker();
      myRangeMarkers = new ArrayList<RangeMarker>();
      myRangeMarkers.add(marker);
      initChunks();
    }
  }

  public final PsiElement getElement() {
    return myUsageInfo.getElement();
  }

  public PsiReference getReference() {
    return getElement().getReference();
  }

  public boolean isNonCodeUsage() {
    return myUsageInfo.isNonCodeUsage;
  }

  public UsageInfo getUsageInfo() {
    return myUsageInfo;
  }

  public int compareTo(final UsageInfo2UsageAdapter o) {
    final PsiElement element = getElement();
    final PsiFile containingFile = element == null ? null : element.getContainingFile();
    final PsiElement oElement = o.getElement();
    final PsiFile oContainingFile = oElement == null ? null : oElement.getContainingFile();
    if (containingFile == null && oContainingFile == null
        || !Comparing.equal(containingFile, oContainingFile)) {
      return 0;
    }
    return getRangeMarker().getStartOffset() - o.getRangeMarker().getStartOffset();
  }

  public void rename(String newName) throws IncorrectOperationException {
    final PsiReference reference = myUsageInfo.getReference();
    assert reference != null : this;
    reference.handleElementRename(newName);
  }

  private class MyUsagePresentation implements UsagePresentation {
    private long myModificationStamp;

    private MyUsagePresentation() {
      myModificationStamp = getCurrentModificationStamp();
    }

    private long getCurrentModificationStamp() {
      final PsiFile containingFile = getElement().getContainingFile();
      return containingFile == null? -1L : containingFile.getModificationStamp();
    }

    @NotNull
    public TextChunk[] getText() {
      if (isValid()) {
        // the check below makes sence only for valid PsiElement
        final long currentModificationStamp = getCurrentModificationStamp();
        if (currentModificationStamp != myModificationStamp) {
          initChunks();
          myModificationStamp = currentModificationStamp;
        }
      }
      return myTextChunks;
    }

    @NotNull
    public String getPlainText() {
      if (myRangeMarkers.isEmpty()) { // element over light virtual file
        return myTextChunks[0].getText();
      }

      int startOffset = ChunkExtractor.getStartOffset(myRangeMarkers);
      final PsiElement element = getElement();
      if (element != null && startOffset != -1) {
        final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
        if (document != null) {
          int lineNumber = document.getLineNumber(startOffset);
          int lineStart = document.getLineStartOffset(lineNumber);
          int lineEnd = document.getLineEndOffset(lineNumber);
          return document.getCharsSequence().subSequence(lineStart, lineEnd).toString();
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
      ArrayList<UsageInfo> list = getSelectedInfoList();
      sink.put(UsageView.USAGE_INFO_LIST_KEY, list);
    }
  }

  private ArrayList<UsageInfo> getSelectedInfoList() {
    ArrayList<UsageInfo> list = new ArrayList<UsageInfo>();
    UsageInfo first = getUsageInfo();
    list.add(first);
    for (int i = 1; i < myRangeMarkers.size(); i++) {
      RangeMarker rangeMarker = myRangeMarkers.get(i);
      PsiElement element = first.getElement();
      if (element == null) continue;
      PsiFile file = element.getContainingFile();
      UsageInfo usageInfo = new UsageInfo(file, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
      list.add(usageInfo);
    }
    return list;
  }
}
