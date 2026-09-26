// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.ElfCandidate;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor text layout storage. Layout is stored on a per-logical-line basis,
 * it's created lazily (when requested) and invalidated on document changes or when explicitly requested.
 *
 * @see LineLayout
 */
@ElfCandidate
final class TextLayoutCache implements PrioritizedDocumentListener, Disposable {
  private static final Logger LOG = Logger.getInstance(TextLayoutCache.class);

  private static final int MAX_CHUNKS_IN_ACTIVE_EDITOR = 1000;
  private static final int MAX_CHUNKS_IN_INACTIVE_EDITOR = 10;
  private static final LineLayout BIDI_NOT_REQUIRED_MARKER = new SingleChunkLayout(null);

  private final EditorView myView;
  private final Document myDocument;
  private final ComponentVisibilityTracker myVisibilityTracker;
  private ArrayList<LineLayout> myLines = new ArrayList<>();
  private int myDocumentChangeOldEndLine;

  private final ObjectLinkedOpenHashSet<LineChunk> laidOutChunks = new ObjectLinkedOpenHashSet<>(MAX_CHUNKS_IN_ACTIVE_EDITOR);
  private final Consumer<LineChunk> removeLaidOutChunk = laidOutChunks::remove;

  TextLayoutCache(EditorView view, ComponentVisibilityTracker visibilityTracker) {
    myView = view;
    myDocument = view.getDocument();
    myVisibilityTracker = visibilityTracker;
    myDocument.addDocumentListener(this, this);
    myVisibilityTracker.runWhenHidden(this, this::trimChunkCache);
  }

  @NotNull LineLayout getLineLayout(int line) {
    checkDisposed();
    if (line >= myLines.size()) {
      LOG.error(
        "Unexpected cache state",
        new Attachment("editorState.txt", myView.getEditor().dumpState())
      );
    }
    LineLayout result = myLines.get(line);
    if (result == null || result == BIDI_NOT_REQUIRED_MARKER) {
      result = LineLayout.createForDocumentLine(myView, line, result == BIDI_NOT_REQUIRED_MARKER);
      myLines.set(line, result);
    }
    return result;
  }

  boolean hasCachedLayoutFor(int line) {
    LineLayout layout = myLines.get(line);
    return layout != null && layout != BIDI_NOT_REQUIRED_MARKER;
  }

  void onChunkAccess(@NotNull LineChunk chunk) {
    if (laidOutChunks.addAndMoveToFirst(chunk) && laidOutChunks.size() > getChunkCacheSizeLimit()) {
      debug();
      laidOutChunks.removeLast().clearFragments();
    }
  }

  void invalidateLines(int startLine, int endLine) {
    invalidateLines(startLine, endLine, endLine, false, false);
  }

  void resetToDocumentSize(boolean documentChangedWithoutNotification) {
    checkDisposed();
    invalidateLines(
      0,
      myLines.size() - 1,
      myDocument.getLineCount() - 1,
      documentChangedWithoutNotification,
      documentChangedWithoutNotification
    );
    if (myLines.size() != myDocument.getLineCount()) {
      LOG.error(
        "Error resetting text layout cache",
        new Attachment("editorState.txt", myView.getEditor().dumpState())
      );
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_LAYOUT_CACHE;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    int startLine = myDocument.getLineNumber(event.getOffset());
    int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
    invalidateLines(
      startLine,
      myDocumentChangeOldEndLine,
      newEndLine,
      true,
      isBidiLayoutRequired(event.getNewFragment())
    );
    if (myLines.size() != myDocument.getLineCount()) {
      LOG.error(
        "Error updating text layout cache after " + event,
        new Attachment("editorState.txt", myView.getEditor().dumpState())
      );
      resetToDocumentSize(true);
    }
  }

  @Override
  public void dispose() {
    myLines = null;
    laidOutChunks.clear();
  }

  private int getChunkCacheSizeLimit() {
    return myVisibilityTracker.isShowing()
           ? MAX_CHUNKS_IN_ACTIVE_EDITOR
           : MAX_CHUNKS_IN_INACTIVE_EDITOR;
  }

  private void invalidateLines(
    int startLine,
    int oldEndLine,
    int newEndLine,
    boolean textChanged,
    boolean bidiRequiredForNewText
  ) {
    checkDisposed();
    if (textChanged) {
      LineLayout firstOldLine = startLine >= 0 && startLine < myLines.size() ? myLines.get(startLine) : null;
      LineLayout lastOldLine = oldEndLine >= 0 && oldEndLine < myLines.size() ? myLines.get(oldEndLine) : null;
      if (firstOldLine == null || lastOldLine == null || !firstOldLine.isLtr() || !lastOldLine.isLtr()) {
        bidiRequiredForNewText = true;
      }
    }
    int endLine = Math.min(oldEndLine, newEndLine);
    for (int line = startLine; line <= endLine; line++) {
      LineLayout lineLayout = myLines.get(line);
      if (lineLayout != null) {
        removeChunksFromCache(lineLayout);
        myLines.set(line, (textChanged && bidiRequiredForNewText) || !lineLayout.isLtr() ? null : BIDI_NOT_REQUIRED_MARKER);
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

  private void removeChunksFromCache(LineLayout layout) {
    layout.forEachChunk(removeLaidOutChunk);
  }

  private void trimChunkCache() {
    int limit = getChunkCacheSizeLimit();
    while (laidOutChunks.size() > limit) {
      debug();
      laidOutChunks.removeLast().clearFragments();
    }
  }

  private void debug() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Clearing chunk for " + myView.getEditor().getVirtualFile());
    }
  }

  private int getAdjustedLineNumber(int offset) {
    return myDocument.getTextLength() == 0 ? -1 : myDocument.getLineNumber(offset);
  }

  private void checkDisposed() {
    if (myLines == null) {
      myView.getEditor().throwDisposalError("Editor is already disposed");
    }
  }

  private static boolean isBidiLayoutRequired(@NotNull CharSequence text) {
    char[] chars = CharArrayUtil.fromSequence(text);
    return Bidi.requiresBidi(chars, 0, chars.length);
  }
}
