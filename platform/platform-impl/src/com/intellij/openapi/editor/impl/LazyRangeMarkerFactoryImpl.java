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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class LazyRangeMarkerFactoryImpl extends LazyRangeMarkerFactory {
  private final Project myProject;
  private static final Key<WeakList<LazyMarker>> LAZY_MARKERS_KEY = Key.create("LAZY_MARKERS_KEY");

  public LazyRangeMarkerFactoryImpl(@NotNull Project project, @NotNull final FileDocumentManager fileDocumentManager) {
    myProject = project;

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        transformRangeMarkers(e);
      }

      @Override
      public void documentChanged(DocumentEvent e) {
        transformRangeMarkers(e);
      }

      private void transformRangeMarkers(@NotNull DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile file = fileDocumentManager.getFile(document);
        if (file == null || myProject.isDisposed()) {
          return;
        }

        WeakList<LazyMarker> lazyMarkers = getMarkers(file);
        if (lazyMarkers == null) {
          return;
        }

        List<LazyMarker> markers = lazyMarkers.toStrongList();
        for (LazyMarker marker : markers) {
          if (file.equals(marker.getFile())) {
            marker.getOrCreateDelegate();
          }
        }
      }
    }, project);
  }

  static WeakList<LazyMarker> getMarkers(@NotNull VirtualFile file) {
    return file.getUserData(LAZY_MARKERS_KEY);
  }

  private static void addToLazyMarkersList(@NotNull LazyMarker marker, @NotNull VirtualFile file) {
    Collection<LazyMarker> markers = getMarkers(file);

    if (markers == null) {
      markers = file.putUserDataIfAbsent(LAZY_MARKERS_KEY, new WeakList<>());
    }
    markers.add(marker);
  }

  private static void removeFromLazyMarkersList(@NotNull LazyMarker marker, @NotNull VirtualFile file) {
    Collection<LazyMarker> markers = getMarkers(file);

    if (markers != null) {
      markers.remove(marker);
    }
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int offset) {
    return ReadAction.compute(() -> {
      // even for already loaded document do not create range marker yet - wait until it really needed when e.g. user clicked to jump to OpenFileDescriptor
      final LazyMarker marker = new OffsetLazyMarker(file, offset);
      addToLazyMarkersList(marker, file);
      return marker;
    });
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int line, final int column, final boolean persistent) {
    return ReadAction.compute(() -> {
      final Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        int myTabSize = CodeStyleFacade.getInstance(myProject).getTabSize(file.getFileType());
        final int offset = calculateOffset(document, line, column, myTabSize);
        return document.createRangeMarker(offset, offset, persistent);
      }

      final LazyMarker marker = new LineColumnLazyMarker(myProject, file, line, column);
      addToLazyMarkersList(marker, file);
      return marker;
    });
  }

  abstract static class LazyMarker extends UserDataHolderBase implements RangeMarker {
    protected RangeMarker myDelegate; // the real range marker which is created only when document is opened, or (this) which means it's disposed
    protected final VirtualFile myFile;
    protected final int myInitialOffset;

    private LazyMarker(@NotNull VirtualFile file, int offset) {
      myFile = file;
      myInitialOffset = offset;
    }

    boolean isDelegated() {
      return myDelegate != null;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    final RangeMarker getOrCreateDelegate() {
      if (myDelegate == null) {
        Document document = FileDocumentManager.getInstance().getDocument(myFile);
        if (document == null) {
          return null;
        }
        myDelegate = createDelegate(myFile, document);
        removeFromLazyMarkersList(this, myFile);
      }
      return isDisposed() ? null : myDelegate;
    }

    @Nullable
    protected abstract RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull Document document);

    @Override
    @NotNull
    public Document getDocument() {
      RangeMarker delegate = getOrCreateDelegate();
      if (delegate == null) {
        //noinspection ConstantConditions
        return FileDocumentManager.getInstance().getDocument(myFile);
      }
      return delegate.getDocument();
    }

    @Override
    public int getStartOffset() {
      return myDelegate == null || isDisposed() ? myInitialOffset : myDelegate.getStartOffset();
    }

    public boolean isDisposed() {
      return myDelegate == this;
    }


    @Override
    public int getEndOffset() {
      return myDelegate == null || isDisposed() ? myInitialOffset : myDelegate.getEndOffset();
    }

    @Override
    public boolean isValid() {
      RangeMarker delegate = getOrCreateDelegate();
      return delegate != null && !isDisposed() && delegate.isValid();
    }

    @Override
    public void setGreedyToLeft(boolean greedy) {
      getOrCreateDelegate().setGreedyToLeft(greedy);
    }

    @Override
    public void setGreedyToRight(boolean greedy) {
      getOrCreateDelegate().setGreedyToRight(greedy);
    }

    @Override
    public boolean isGreedyToRight() {
      return getOrCreateDelegate().isGreedyToRight();
    }

    @Override
    public boolean isGreedyToLeft() {
      return getOrCreateDelegate().isGreedyToLeft();
    }

    @Override
    public void dispose() {
      assert !isDisposed();
      RangeMarker delegate = myDelegate;
      if (delegate == null) {
        removeFromLazyMarkersList(this, myFile);
        myDelegate = this; // mark of disposed marker
      }
      else {
        delegate.dispose();
      }
    }
  }

  private static class OffsetLazyMarker extends LazyMarker {
    private OffsetLazyMarker(@NotNull VirtualFile file, int offset) {
      super(file, offset);
    }

    @Override
    public boolean isValid() {
      RangeMarker delegate = myDelegate;
      if (delegate == null) {
        Document document = FileDocumentManager.getInstance().getDocument(myFile);
        return document != null;
      }

      return super.isValid();
    }

    @Override
    @NotNull
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull final Document document) {
      final int offset = Math.min(myInitialOffset, document.getTextLength());
      return document.createRangeMarker(offset, offset);
    }
  }

  private static class LineColumnLazyMarker extends LazyMarker {
    private final int myLine;
    private final int myColumn;
    private final int myTabSize;

    private LineColumnLazyMarker(@NotNull Project project, @NotNull VirtualFile file, int line, int column) {
      super(file, -1);
      myLine = line;
      myColumn = column;
      myTabSize = CodeStyleFacade.getInstance(project).getTabSize(file.getFileType());
    }

    @Override
    @Nullable
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull Document document) {
      if (document.getTextLength() == 0 && !(myLine == 0 && myColumn == 0)) {
        return null;
      }

      int offset = calculateOffset(document, myLine, myColumn, myTabSize);
      return document.createRangeMarker(offset, offset);
    }

    @Override
    public boolean isValid() {
      RangeMarker delegate = myDelegate;
      if (delegate == null) {
        Document document = FileDocumentManager.getInstance().getDocument(myFile);
        return document != null && (document.getTextLength() != 0 || myLine == 0 && myColumn == 0);
      }

      return super.isValid();
    }

    @Override
    public int getStartOffset() {
      getOrCreateDelegate();
      return super.getStartOffset();
    }

    @Override
    public int getEndOffset() {
      getOrCreateDelegate();
      return super.getEndOffset();
    }
  }

  private static int calculateOffset(@NotNull Document document,
                                     final int line,
                                     final int column,
                                     int tabSize) {
    int offset;
    if (0 <= line && line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }

}
