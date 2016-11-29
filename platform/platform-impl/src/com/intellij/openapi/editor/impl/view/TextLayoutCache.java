/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Editor text layout storage. Layout is stored on a per-logical-line basis, 
 * it's created lazily (when requested) and invalidated on document changes or when explicitly requested.
 * 
 * @see LineLayout
 */
class TextLayoutCache implements PrioritizedDocumentListener, Disposable {
  private static final Logger LOG = Logger.getInstance(TextLayoutCache.class);
  
  private static final int MAX_CHUNKS_IN_ACTIVE_EDITOR = 1000;
  private static final int MAX_CHUNKS_IN_INACTIVE_EDITOR = 10;
  
  private final EditorView myView;
  private final Document myDocument;
  private final LineLayout myBidiNotRequiredMarker;
  private ArrayList<LineLayout> myLines = new ArrayList<>();
  private int myDocumentChangeOldEndLine;
  
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") 
  private LinkedHashMap<LineLayout.Chunk, Object> myLaidOutChunks = 
    new LinkedHashMap<LineLayout.Chunk, Object>(MAX_CHUNKS_IN_ACTIVE_EDITOR, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<LineLayout.Chunk, Object> eldest) {
        if (size() > getChunkCacheSizeLimit()) {
          if (LOG.isDebugEnabled()) LOG.debug("Clearing chunk for " + myView.getEditor().getVirtualFile());
          eldest.getKey().clearCache();
          return true;
        }
        return false;
      }
    };

  TextLayoutCache(EditorView view) {
    myView = view;
    myDocument = view.getEditor().getDocument();
    myDocument.addDocumentListener(this, this);
    myBidiNotRequiredMarker = LineLayout.create(view, "", Font.PLAIN);
    Disposer.register(this, new UiNotifyConnector(view.getEditor().getContentComponent(), new Activatable.Adapter() {
      @Override
      public void hideNotify() {
        trimChunkCache();
      }
    }));
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_LAYOUT_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    int startLine = myDocument.getLineNumber(event.getOffset());
    int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
    invalidateLines(startLine, myDocumentChangeOldEndLine, newEndLine, true, LineLayout.isBidiLayoutRequired(event.getNewFragment()));
  }

  @Override
  public void dispose() {
    myLines = null;
    myLaidOutChunks = null;
  }

  private int getAdjustedLineNumber(int offset) {
    return myDocument.getTextLength() == 0 ? -1 : myDocument.getLineNumber(offset);
  }

  void resetToDocumentSize(boolean documentChangedWithoutNotification) {
    checkDisposed();
    invalidateLines(0, myLines.size() - 1, myDocument.getLineCount() - 1,
                    documentChangedWithoutNotification, documentChangedWithoutNotification);
  }

  void invalidateLines(int startLine, int endLine) {
    invalidateLines(startLine, endLine, endLine, false, false);
  }

  private void invalidateLines(int startLine, int oldEndLine, int newEndLine, boolean textChanged, boolean bidiRequiredForNewText) {
    checkDisposed();

    if (textChanged) {
      LineLayout firstOldLine = startLine >= 0 && startLine < myLines.size() ? myLines.get(startLine) : null;
      LineLayout lastOldLine = oldEndLine >= 0 && oldEndLine < myLines.size() ? myLines.get(oldEndLine) : null;
      if (firstOldLine == null || lastOldLine == null || !firstOldLine.isLtr() || !lastOldLine.isLtr()) bidiRequiredForNewText = true;
    }

    int endLine = Math.min(oldEndLine, newEndLine);
    for (int line = startLine; line <= endLine; line++) {
      LineLayout lineLayout = myLines.get(line);
      if (lineLayout != null) {
        removeChunksFromCache(lineLayout);
        myLines.set(line, (textChanged && bidiRequiredForNewText) || !lineLayout.isLtr() ? null : myBidiNotRequiredMarker);
      }
    }
    if (oldEndLine < newEndLine) {
      myLines.addAll(oldEndLine + 1, Collections.nCopies(newEndLine - oldEndLine, null));
    } else if (oldEndLine > newEndLine) {
      List<LineLayout> layouts = myLines.subList(newEndLine + 1, oldEndLine + 1);
      for (LineLayout layout : layouts) {
        if (layout != null) {
          removeChunksFromCache(layout);
        }
      }
      layouts.clear();
    }
  }

  @NotNull
  LineLayout getLineLayout(int line) {
    checkDisposed();
    if (line >= myLines.size()) LOG.error("Unexpected cache state", new Attachment("editorState.txt", myView.getEditor().dumpState()));
    LineLayout result = myLines.get(line);
    if (result == null || result == myBidiNotRequiredMarker) {
      result = LineLayout.create(myView, line, result == myBidiNotRequiredMarker);
      myLines.set(line, result);
    }
    return result;
  }

  boolean hasCachedLayoutFor(int line) {
    LineLayout layout = myLines.get(line);
    return layout != null && layout != myBidiNotRequiredMarker;
  }

  private int getChunkCacheSizeLimit() {
    return myView.getEditor().getContentComponent().isShowing() ? MAX_CHUNKS_IN_ACTIVE_EDITOR : MAX_CHUNKS_IN_INACTIVE_EDITOR;
  }

  void onChunkAccess(LineLayout.Chunk chunk) {
    myLaidOutChunks.put(chunk, null);
  }

  private void removeChunksFromCache(LineLayout layout) {
    layout.getChunksInLogicalOrder().forEach(myLaidOutChunks::remove);
  }

  private void trimChunkCache() {
    int limit = getChunkCacheSizeLimit();
    if (myLaidOutChunks.size() > limit) {
      Iterator<LineLayout.Chunk> it = myLaidOutChunks.keySet().iterator();
      while (myLaidOutChunks.size() > limit) {
        LineLayout.Chunk chunk = it.next();
        if (LOG.isDebugEnabled()) LOG.debug("Clearing chunk for " + myView.getEditor().getVirtualFile());
        chunk.clearCache();
        it.remove();
      }
    }
  }

  private void checkDisposed() {
    if (myLines == null) myView.getEditor().throwDisposalError("Editor is already disposed");
  }
}
