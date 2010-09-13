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
package com.intellij.openapi.editor.ex.util;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class LexerEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.LexerEditorHighlighter");
  private HighlighterClient myEditor;
  private final Lexer myLexer;
  private final Map<IElementType, TextAttributes> myAttributesMap;
  private SegmentArrayWithData mySegments;
  private final SyntaxHighlighter myHighlighter;
  private EditorColorsScheme myScheme;
  private final int myInitialState;
  public static final Key<Integer> CHANGED_TOKEN_START_OFFSET = Key.create("CHANGED_TOKEN_START_OFFSET");

  public LexerEditorHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myLexer.start(ArrayUtil.EMPTY_CHAR_SEQUENCE);
    myInitialState = myLexer.getState();
    myAttributesMap = new HashMap<IElementType, TextAttributes>();
    myHighlighter = highlighter;
    mySegments = new SegmentArrayWithData();
  }

  public boolean isPlain() {
    return myHighlighter instanceof PlainSyntaxHighlighter;
  }

  @Nullable
  protected final Document getDocument() {
    return myEditor != null ? myEditor.getDocument() : null;
  }

  public synchronized final boolean checkContentIsEqualTo(CharSequence sequence) {
    final Document document = getDocument();
    if (document != null) return document.getText().equals(sequence.toString());
    return false;
  }

  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  protected final void setSegmentStorage(SegmentArrayWithData storage) {
    mySegments = storage;
  }

  public Lexer getLexer() {
    return myLexer;
  }

  public void setEditor(HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    myScheme = scheme;
    myAttributesMap.clear();
  }

  public HighlighterIterator createIterator(int startOffset) {
    synchronized (this) {
      final Document document = getDocument();
      if(document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) {
        ((DocumentEx)document).setInBulkUpdate(false); // bulk mode failed
      }

      if (mySegments.getSegmentCount() == 0 && document != null && document.getTextLength() > 0) {
        // bulk mode was reset
        doSetText(document.getCharsSequence());
      }

      final int latestValidOffset = mySegments.getLastValidOffset();
      return new HighlighterIteratorImpl(startOffset <= latestValidOffset ? startOffset : latestValidOffset);
    }
  }

  private int packData(IElementType tokenType, int state) {
    final short idx = tokenType.getIndex();
    return state == myInitialState ? idx : -idx;
  }

  private static boolean isInitialState(int data) {
    return data >= 0;
  }

  protected static IElementType unpackToken(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  public synchronized void documentChanged(DocumentEvent e) {
    final Document document = e.getDocument();

    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) {
      mySegments.removeAll();
      return;
    }

    if(mySegments.getSegmentCount() == 0) {
      setText(document.getCharsSequence());
      return;
    }

    CharSequence text = document.getCharsSequence();
    int oldStartOffset = e.getOffset();

    final int segmentIndex;
    try {
      segmentIndex = mySegments.findSegmentIndex(oldStartOffset) - 2;
    }
    catch (IndexOutOfBoundsException ex) {
      throw new IndexOutOfBoundsException(ex.getMessage() + " Lexer: " + myLexer);
    }
    final int oldStartIndex = Math.max(0, segmentIndex);
    int startIndex = oldStartIndex;

    int data;
    do {
      data = mySegments.getSegmentData(startIndex);
      if (isInitialState(data)|| startIndex == 0) break;
      startIndex--;
    }
    while (true);

    int startOffset = mySegments.getSegmentStart(startIndex);
    int newEndOffset = e.getOffset() + e.getNewLength();

    myLexer.start(text, startOffset, text.length(), myInitialState);

    int lastTokenStart = -1;
    int lastLexerState = -1;

    while (myLexer.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = myLexer.getTokenStart();
      int lexerState = myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + document.getText());
      }

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), lexerState);
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      myLexer.advance();
      lastTokenStart = tokenStart;
      lastLexerState = lexerState;
    }

    startOffset = mySegments.getSegmentStart(startIndex);
    int repaintEnd = -1;
    int insertSegmentCount = 0;
    int oldEndIndex = -1;
    SegmentArrayWithData insertSegments = new SegmentArrayWithData();

    while(myLexer.getTokenType() != null) {
      int tokenStart = myLexer.getTokenStart();
      int lexerState = myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + document.getText());
      }

      lastTokenStart = tokenStart;
      lastLexerState = lexerState;

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), lexerState);
      if(tokenStart >= newEndOffset && lexerState == myInitialState) {
        int shiftedTokenStart = tokenStart - e.getNewLength() + e.getOldLength();
        int index = mySegments.findSegmentIndex(shiftedTokenStart);
        if (mySegments.getSegmentStart(index) == shiftedTokenStart && mySegments.getSegmentData(index) == data) {
          repaintEnd = tokenStart;
          oldEndIndex = index;
          break;
        }
      }
      insertSegments.setElementAt(insertSegmentCount, tokenStart, tokenEnd, data);
      insertSegmentCount++;
      myLexer.advance();
    }

    final int shift = e.getNewLength() - e.getOldLength();
    if (repaintEnd > 0) {
      while (insertSegmentCount > 0 && oldEndIndex > startIndex) {
        if (!segmentsEqual(mySegments, oldEndIndex - 1, insertSegments, insertSegmentCount - 1, shift)) {
          break;
        }
        insertSegmentCount--;
        oldEndIndex--;
        repaintEnd = insertSegments.getSegmentStart(insertSegmentCount);
        insertSegments.remove(insertSegmentCount, insertSegmentCount + 1);
      }
    }

    if(repaintEnd == -1) {
      repaintEnd = text.length();
    }

    if (oldEndIndex < 0){
      oldEndIndex = mySegments.getSegmentCount();
    }
    mySegments.shiftSegments(oldEndIndex, shift);
    mySegments.replace(startIndex, oldEndIndex, insertSegments);

    if (insertSegmentCount == 0 ||
        oldEndIndex == startIndex + 1 && insertSegmentCount == 1 && data == mySegments.getSegmentData(startIndex)) {
      return;
    }

    myEditor.repaint(startOffset, repaintEnd);
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public int getPriority() {
    return EditorDocumentPriorities.LEXER_EDITOR;
  }

  private static boolean segmentsEqual(SegmentArrayWithData a1, int idx1, SegmentArrayWithData a2, int idx2, final int offsetShift) {
    return a1.getSegmentStart(idx1) + offsetShift == a2.getSegmentStart(idx2) &&
           a1.getSegmentEnd(idx1) + offsetShift == a2.getSegmentEnd(idx2) &&
           a1.getSegmentData(idx1) == a2.getSegmentData(idx2);
  }

  public HighlighterClient getClient() {
    return myEditor;
  }

  public void setText(CharSequence text) {
    synchronized (this) {
      doSetText(text);
    }
  }

  protected class TokenProcessor {
    public void addToken(final int i, final int startOffset, final int endOffset, final int data, final IElementType tokenType) {
      mySegments.setElementAt(i, startOffset, endOffset, data);
    }

    public void finish() {
    }
  }

  private void doSetText(final CharSequence text) {
    final TokenProcessor processor = createTokenProcessor(0);
    myLexer.start(text, 0, text.length(),myInitialState);
    mySegments.removeAll();
    int i = 0;
    while (true) {
      final IElementType tokenType = myLexer.getTokenType();
      if (tokenType == null) break;

      int data = packData(tokenType, myLexer.getState());
      processor.addToken(i, myLexer.getTokenStart(), myLexer.getTokenEnd(), data, tokenType);
      i++;
      myLexer.advance();
    }
    processor.finish();

    if(myEditor != null) {
      Runnable repaint = new DumbAwareRunnable() {
        public void run() {
          myEditor.repaint(0, text.length());
        }
      };

      if (ApplicationManager.getApplication().isDispatchThread()) repaint.run();
      else ApplicationManager.getApplication().invokeLater(repaint);
    }
  }

  protected TokenProcessor createTokenProcessor(final int startIndex) {
    return new TokenProcessor();
  }

  private TextAttributes getAttributes(IElementType tokenType) {
    TextAttributes attrs = myAttributesMap.get(tokenType);
    if (attrs == null) {
      attrs = convertAttributes(myHighlighter.getTokenHighlights(tokenType));
      myAttributesMap.put(tokenType, attrs);
    }
    return attrs;
  }

  protected TextAttributes convertAttributes(TextAttributesKey[] keys) {
    EditorColorsScheme scheme = myScheme;
    TextAttributes attrs = scheme.getAttributes(HighlighterColors.TEXT);
    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = scheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }
    return attrs;
  }

  public class HighlighterIteratorImpl implements HighlighterIterator {
    private int mySegmentIndex = 0;

    HighlighterIteratorImpl(int startOffset) {
      mySegmentIndex = mySegments.findSegmentIndex(startOffset);
    }

    public int currentIndex() {
      return mySegmentIndex;
    }

    public TextAttributes getTextAttributes() {
      return getAttributes(getTokenType());
    }

    public int getStart() {
      return mySegments.getSegmentStart(mySegmentIndex);
    }

    public int getEnd() {
      return mySegments.getSegmentEnd(mySegmentIndex);
    }

    public IElementType getTokenType(){
      return unpackToken(mySegments.getSegmentData(mySegmentIndex));
    }

    public void advance() {
      mySegmentIndex++;
    }

    public void retreat(){
      mySegmentIndex--;
    }

    public boolean atEnd() {
      return mySegmentIndex >= mySegments.getSegmentCount() || mySegmentIndex < 0;
    }

    public Document getDocument() {
      return myEditor.getDocument();
    }
  }
}
