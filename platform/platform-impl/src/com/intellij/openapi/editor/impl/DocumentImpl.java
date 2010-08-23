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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class DocumentImpl extends UserDataHolderBase implements DocumentEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentImpl");

  private final List<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private final WeakHashMap<RangeMarkerEx,String> myRangeMarkers = new WeakHashMap<RangeMarkerEx, String>();
  private final List<RangeMarker> myGuardedBlocks = new ArrayList<RangeMarker>();
  private ReadonlyFragmentModificationHandler myReadonlyFragmentModificationHandler;

  private final LineSet myLineSet = new LineSet();
  private final CharArray myText = new CharArray(0) {
    protected DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
      return DocumentImpl.this.beforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
    }

    protected void afterChangedUpdate(DocumentEvent event, long newModificationStamp) {
      changedUpdate(event, newModificationStamp);
    }
  };

  private boolean myIsReadOnly = false;
  private boolean isStripTrailingSpacesEnabled = true;
  private volatile long myModificationStamp;
  private final ConcurrentMap<Project, MarkupModel> myProjectToMarkupModelMap = new ConcurrentHashMap<Project, MarkupModel>();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private volatile MarkupModelEx myMarkupModel;
  private DocumentListener[] myCachedDocumentListeners;
  private final List<EditReadOnlyListener> myReadOnlyListeners = new ArrayList<EditReadOnlyListener>(1);

  /**
   * Comparator that sorts {@link DocumentListener} objects by their {@link PrioritizedDocumentListener#getPriority() priorities} (if any).
   */
  public static final Comparator<? super DocumentListener> ourListenersByPriorityComparator = new Comparator<Object>() {
    public int compare(Object o1, Object o2) {
      return getPriority(o1) - getPriority(o2);
    }

    private int getPriority(Object o) {
      if (o instanceof PrioritizedDocumentListener) return ((PrioritizedDocumentListener)o).getPriority();
      return Integer.MAX_VALUE;
    }
  };

  private int myCheckGuardedBlocks = 0;
  private boolean myGuardsSuppressed = false;
  private boolean myEventsHandling = false;
  private final boolean myAssertWriteAccess;
  private boolean myDoingBulkUpdate = false;
  private static final Key<WeakReference<EditorHighlighter>> ourSomeEditorSyntaxHighlighter = Key.create("some editor highlighter");
  private boolean myAcceptSlashR = false;

  private DocumentImpl() {
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertWriteAccess = true;
  }

  public DocumentImpl(String text) {
    this((CharSequence)text);
  }

  public DocumentImpl(boolean forUseInNonAWTThread) {
    setCyclicBufferSize(0);
    setModificationStamp(LocalTimeCounter.currentTime());
    myAssertWriteAccess = !forUseInNonAWTThread;
  }

  public boolean setAcceptSlashR(boolean accept) {
    try {
      return myAcceptSlashR;
    }
    finally {
      myAcceptSlashR = accept;
    }
  }

  public DocumentImpl(CharSequence chars) {
    this();
    assertValidSeparators(chars);
    myText.setText(chars);
    DocumentEvent event = new DocumentEventImpl(this, 0, null, null, -1, true);
    myLineSet.documentCreated(event);
  }

  public char[] getRawChars() {
    return myText.getChars();
  }

  @NotNull
  public char[] getChars() {
    return CharArrayUtil.fromSequence(getCharsSequence());
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return getMarkupModel(null);
  }

  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    isStripTrailingSpacesEnabled = isEnabled;
  }

  public void stripTrailingSpaces(boolean inChangedLinesOnly) {
    Editor[] editors = EditorFactory.getInstance().getEditors(this, null);
    VisualPosition[] visualCarets = new VisualPosition[editors.length];
    int[] caretLines = new int[editors.length];
    for (int i = 0; i < editors.length; i++) {
      visualCarets[i] = editors[i].getCaretModel().getVisualPosition();
      caretLines[i] = editors[i].getCaretModel().getLogicalPosition().line;
    }

    if (!isStripTrailingSpacesEnabled) {
      return;
    }

    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();

    lines:
        for (int i = 0; i < myLineSet.getLineCount(); i++) {
          if (!isTestMode) {
            for (int caretLine : caretLines) {
              if (caretLine == i) continue lines;
            }
          }

          if (!inChangedLinesOnly || myLineSet.isModified(i)) {
            int start = -1;
            int lineEnd = myLineSet.getLineEnd(i) - myLineSet.getSeparatorLength(i);
            int lineStart = myLineSet.getLineStart(i);
            CharSequence text = myText.getCharArray();
            for (int offset = lineEnd - 1; offset >= lineStart; offset--) {
              char c = text.charAt(offset);
              if (c != ' ' && c != '\t') {
                break;
              }
              start = offset;
            }
            if (start != -1) {
              deleteString(start, lineEnd);
            }
          }
        }

    if (!ShutDownTracker.isShutdownHookRunning()) {
      for (int i = 0; i < editors.length; i++) {
        editors[i].getCaretModel().moveToVisualPosition(visualCarets[i]);
      }
    }
  }

  public void setReadOnly(boolean isReadOnly) {
    if (myIsReadOnly != isReadOnly) {
      myIsReadOnly = isReadOnly;
      myPropertyChangeSupport.firePropertyChange(PROP_WRITABLE, !isReadOnly, isReadOnly);
    }
  }

  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentModificationHandler;
  }

  public void setReadonlyFragmentModificationHandler(final ReadonlyFragmentModificationHandler readonlyFragmentModificationHandler) {
    myReadonlyFragmentModificationHandler = readonlyFragmentModificationHandler;
  }

  public boolean isWritable() {
    return !myIsReadOnly;
  }

  public void removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    synchronized(myRangeMarkers) {
      myRangeMarkers.remove(rangeMarker);
    }
  }

  public void addRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    synchronized(myRangeMarkers) {
      myRangeMarkers.put(rangeMarker, null);
    }
  }

  @TestOnly
  public Collection<RangeMarkerEx> getRangeMarkers() {
    return myRangeMarkers.keySet();
  }

  @NotNull
  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    LOG.assertTrue(startOffset <= endOffset, "Should be startOffset <= endOffset");
    RangeMarker block = createRangeMarker(startOffset, endOffset, true);
    myGuardedBlocks.add(block);
    return block;
  }

  public void removeGuardedBlock(@NotNull RangeMarker block) {
    myGuardedBlocks.remove(block);
  }

  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return myGuardedBlocks;
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  public RangeMarker getOffsetGuard(int offset) {
    for (int i = 0; i < myGuardedBlocks.size(); i++) {
      RangeMarker block = myGuardedBlocks.get(i);
      if (offsetInRange(offset, block.getStartOffset(), block.getEndOffset())) return block;
    }

    return null;
  }

  public RangeMarker getRangeGuard(int start, int end) {
    for (RangeMarker block : myGuardedBlocks) {
      if (rangeIntersect(new int[]{start, block.getStartOffset()}, new int[]{end, block.getEndOffset()}, new boolean[]{true, block.isGreedyToLeft()}, new boolean[]{true, block.isGreedyToRight()})) {
        return block;
      }
    }

    return null;
  }

  public void startGuardedBlockChecking() {
    myCheckGuardedBlocks++;
  }

  public void stopGuardedBlockChecking() {
    LOG.assertTrue(myCheckGuardedBlocks > 0, "Unpaired start/stopGuardedBlockChecking");
    myCheckGuardedBlocks--;
  }

  private static boolean offsetInRange(int offset, int start, int end) {
    return start <= offset && offset < end;
  }

  private static boolean rangeIntersect(int[] start, int[] end, boolean[] leftInclusive, boolean[] rightInclusive) {
    if (start[0] > start[1] || start[0] == start[1] && !leftInclusive[0]) {
      ArrayUtil.swap(start, 0, 1);
      ArrayUtil.swap(end, 0, 1);
      ArrayUtil.swap(leftInclusive, 0, 1);
      ArrayUtil.swap(rightInclusive, 0, 1);
    }
    if (end[0] < start[1]) return false;
    if (end[0] > start[1]) return true;

    return leftInclusive[1] && rightInclusive[0];
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return new RangeMarkerImpl(this, startOffset, endOffset);
  }

  @NotNull
  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    if (!(0 <= startOffset && startOffset <= endOffset && endOffset <= getTextLength())) {
      LOG.error("Incorrect offsets startOffset=" + startOffset + ", endOffset=" + endOffset + ", text length=" + getTextLength());
    }
    return surviveOnExternalChange
           ? new PersistentRangeMarker(this, startOffset, endOffset)
           : new RangeMarkerImpl(this, startOffset, endOffset);
  }

  public long getModificationStamp() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    replaceString(0, getTextLength(), chars, newModificationStamp, true); //TODO: optimization!!!
    clearLineModificationFlags();
  }

  public int getListenersCount() {
    return myDocumentListeners.size();
  }

  public void insertString(int offset, @NotNull CharSequence s) {
    if (offset < 0) throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    if (offset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset +"; documentLength: "+getTextLength()+ "; " + s.subSequence(Math.max(0, getTextLength() - 20), getTextLength()));
    }
    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (s.length() == 0) return;

    RangeMarker marker = getRangeGuard(offset, offset);
    if (marker != null) {
      throwGuardedFragment(marker, offset, null, s.toString());
    }

    myText.insert(s, offset);
  }

  public void deleteString(int startOffset, int endOffset) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    if (!isWritable()) throw new ReadOnlyModificationException(this);
    if (startOffset == endOffset) return;
    CharSequence sToDelete = myText.substring(startOffset, endOffset);

    RangeMarker marker = getRangeGuard(startOffset, endOffset);
    if (marker != null) {
      throwGuardedFragment(marker, startOffset, sToDelete.toString(), null);
    }

    myText.remove(startOffset, endOffset,sToDelete);
  }

  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    replaceString(startOffset, endOffset, s, LocalTimeCounter.currentTime(), startOffset==0 && endOffset==getTextLength());
  }

  private void replaceString(int startOffset, int endOffset, CharSequence s, final long newModificationStamp, boolean wholeTextReplaced) {
    assertBounds(startOffset, endOffset);

    assertWriteAccess();
    assertValidSeparators(s);

    if (!isWritable()) {
      throw new ReadOnlyModificationException(this);
    }
    final int newStringLength = s.length();
    final CharSequence chars = getCharsSequence();
    int newStartInString = 0;
    int newEndInString = newStringLength;
    while (newStartInString < newStringLength &&
           startOffset < endOffset &&
           s.charAt(newStartInString) == chars.charAt(startOffset)) {
      startOffset++;
      newStartInString++;
    }

    while(endOffset > startOffset &&
          newEndInString > newStartInString &&
          s.charAt(newEndInString - 1) == chars.charAt(endOffset - 1)){
      newEndInString--;
      endOffset--;
    }
    //if (newEndInString - newStartInString == 0 && startOffset == endOffset) {
      //setModificationStamp(newModificationStamp);
      //return;
    //}

    s = s.subSequence(newStartInString, newEndInString);
    CharSequence sToDelete = myText.substring(startOffset, endOffset);
    RangeMarker guard = getRangeGuard(startOffset, endOffset);
    if (guard != null) {
      throwGuardedFragment(guard, startOffset, sToDelete.toString(), s.toString());
    }

    myText.replace(startOffset, endOffset, sToDelete, s,newModificationStamp, wholeTextReplaced);
  }

  private void assertBounds(final int startOffset, final int endOffset) {
    if (startOffset < 0 || startOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong startOffset: " + startOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < 0 || endOffset > getTextLength()) {
      throw new IndexOutOfBoundsException("Wrong endOffset: " + endOffset+"; documentLength: "+getTextLength());
    }
    if (endOffset < startOffset) {
      throw new IllegalArgumentException("endOffset < startOffset: " + endOffset + " < " + startOffset+"; documentLength: "+getTextLength());
    }
  }

  private void assertWriteAccess() {
    if (myAssertWriteAccess) {
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.assertWriteAccessAllowed();
      }
    }
  }

  private void assertValidSeparators(final CharSequence s) {
    if (myAcceptSlashR) return;
    StringUtil.assertValidSeparators(s);
  }

  private void throwGuardedFragment(RangeMarker guard, int offset, String oldString, String newString) {
    if (myCheckGuardedBlocks > 0 && !myGuardsSuppressed) {
      DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, false);
      throw new ReadOnlyFragmentModificationException(event, guard);
    }
  }

  public void suppressGuardedExceptions() {
    myGuardsSuppressed = true;
  }

  public void unSuppressGuardedExceptions() {
    myGuardsSuppressed = false;
  }

  public boolean isInEventsHandling() {
    return myEventsHandling;
  }

  public void clearLineModificationFlags() {
    myLineSet.clearModificationFlags();
  }

  private DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString, boolean wholeTextReplaced) {
    if (ShutDownTracker.isShutdownHookRunning()) {
      return null; // suppress events in shutdown hook
    }
    DocumentEvent event = new DocumentEventImpl(this, offset, oldString, newString, myModificationStamp, wholeTextReplaced);

    DocumentListener[] listeners = getCachedListeners();
    for (int i = listeners.length - 1; i >= 0; i--) {
      try {
        listeners[i].beforeDocumentChange(event);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    myEventsHandling = true;
    return event;
  }

  private void changedUpdate(DocumentEvent event, long newModificationStamp) {
    if (ShutDownTracker.isShutdownHookRunning()) {
      return; // suppress events in shutdown hook
    }
    try{
      if (LOG.isDebugEnabled()) LOG.debug(event.toString());

      myLineSet.changedUpdate(event);
      setModificationStamp(newModificationStamp);

      updateRangeMarkers(event);

      DocumentListener[] listeners = getCachedListeners();
      for (DocumentListener listener : listeners) {
        try {
          listener.documentChanged(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    finally{
      myEventsHandling = false;
    }
  }

  private void updateRangeMarkers(final DocumentEvent event) {
    synchronized(myRangeMarkers) {
      for(Iterator<RangeMarkerEx> rangeMarkerIterator = myRangeMarkers.keySet().iterator(); rangeMarkerIterator.hasNext();) {
        try {
          final RangeMarkerEx rangeMarker = rangeMarkerIterator.next();

          if (rangeMarker != null && rangeMarker.isValid()) {
            if (event.getOffset() <= rangeMarker.getEndOffset()) {
              rangeMarker.documentChanged(event);
              if (!rangeMarker.isValid()) {
                rangeMarkerIterator.remove();
                if (myGuardedBlocks.remove(rangeMarker)) {
                  LOG.error("Guarded blocks should stay valid: "+rangeMarker);
                }
              }
            }
          }
          else {
            rangeMarkerIterator.remove();
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  public String getText() {
    assertReadAccessToDocumentsAllowed();
    return myText.toString();
  }

  @Override
  public String getText(TextRange range) {
    assertReadAccessToDocumentsAllowed();
    return myText.substring(range.getStartOffset(), range.getEndOffset()).toString();
  }

  public int getTextLength() {
    assertReadAccessToDocumentsAllowed();
    return myText.length();
  }

  private static void assertReadAccessToDocumentsAllowed() {
    /*
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (application != null) {
      application.assertReadAccessToDocumentsAllowed();
    }
    */
  }

/*
  This method should be used very carefully - only to read the array, and to be sure, that nobody changes
  text, while this array is processed.
  Really it is used only to optimize paint in Editor.
  [Valentin] 25.04.2001: More really, it is used in 61 places in 29 files across the project :-)))
*/

  CharSequence getCharsNoThreadCheck() {
    return myText.getCharArray();
  }

  @NotNull
  public CharSequence getCharsSequence() {
    assertReadAccessToDocumentsAllowed();
    return myText.getCharArray();
  }


  public void addDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    LOG.assertTrue(!myDocumentListeners.contains(listener), listener);
    myDocumentListeners.add(listener);
  }

  public void addDocumentListener(@NotNull final DocumentListener listener, @NotNull Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  public void removeDocumentListener(@NotNull DocumentListener listener) {
    myCachedDocumentListeners = null;
    boolean success = myDocumentListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public int getLineNumber(int offset) {
    assertReadAccessToDocumentsAllowed();
    int lineIndex = myLineSet.findLineIndex(offset);
    assert lineIndex >= 0;
    return lineIndex;
  }

  @NotNull
  public LineIterator createLineIterator() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    return myLineSet.createIterator();
  }

  public final int getLineStartOffset(int line) {
    assertReadAccessToDocumentsAllowed();
    if (line == 0) return 0; // otherwise it crashed for zero-length document
    int lineStart = myLineSet.getLineStart(line);
    assert lineStart >= 0;
    return lineStart;
  }

  public final int getLineEndOffset(int line) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    if (getTextLength() == 0 && line == 0) return 0;
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  public final int getLineSeparatorLength(int line) {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    int separatorLength = myLineSet.getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  public final int getLineCount() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    int lineCount = myLineSet.getLineCount();
    assert lineCount >= 0;
    return lineCount;
  }

  private DocumentListener[] getCachedListeners() {
    if (myCachedDocumentListeners == null) {
      Collections.sort(myDocumentListeners, ourListenersByPriorityComparator);
      myCachedDocumentListeners = myDocumentListeners.toArray(new DocumentListener[myDocumentListeners.size()]);
    }

    return myCachedDocumentListeners;
  }

  public void fireReadOnlyModificationAttempt() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessToDocumentsAllowed();
    EditReadOnlyListener[] listeners = myReadOnlyListeners.toArray(
      new EditReadOnlyListener[myReadOnlyListeners.size()]);
    for (EditReadOnlyListener listener : listeners) {
      listener.readOnlyModificationAttempt(this);
    }
  }

  public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.add(listener);
  }

  public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) {
    myReadOnlyListeners.remove(listener);
  }

  public void removeMarkupModel(Project project) {
    MarkupModel model = myProjectToMarkupModelMap.remove(project);
    if (model != null) {
      ((MarkupModelEx)model).dispose();
    }
  }

  @NotNull
  public MarkupModel getMarkupModel(Project project) {
    return getMarkupModel(project, true);
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  private final Object lock = new Object();
  public MarkupModel getMarkupModel(@Nullable Project project, boolean create) {
    if (project == null) {
      MarkupModelEx markupModel = myMarkupModel;
      if (create && markupModel == null) {
        synchronized (lock) {
          markupModel = myMarkupModel;
          if (markupModel == null) {
            myMarkupModel = markupModel = new MarkupModelImpl(this);
          }
        }
      }
      return markupModel;
    }

    final DocumentMarkupModelManager documentMarkupModelManager = project.isDisposed() ? null : DocumentMarkupModelManager.getInstance(project);
    if (documentMarkupModelManager == null || documentMarkupModelManager.isDisposed()) {
      return new EmptyMarkupModel(this);
    }

    MarkupModel model = myProjectToMarkupModelMap.get(project);
    if (create && model == null) {
      model = ConcurrencyUtil.cacheOrGet(myProjectToMarkupModelMap, project, new MarkupModelImpl(this));
      documentMarkupModelManager.registerDocument(this);
    }

    return model;
  }

  public void setCyclicBufferSize(int bufferSize) {
    myText.setBufferSize(bufferSize);
  }

  public void setText(@NotNull final CharSequence text) {
    Runnable runnable = new Runnable() {
      public void run() {
        replaceString(0, getTextLength(), text, LocalTimeCounter.currentTime(), true);
      }
    };
    if (CommandProcessor.getInstance().isUndoTransparentActionInProgress()) {
      runnable.run();
    }
    else {
      CommandProcessor.getInstance().executeCommand(null, runnable, "", DocCommandGroupId.noneGroupId(this));
    }

    clearLineModificationFlags();
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public final boolean isInBulkUpdate() {
    return myDoingBulkUpdate;
  }

  public final void setInBulkUpdate(boolean value) {
    if (value) {
      myDoingBulkUpdate = true;
      getPublisher().updateStarted(this);
    }
    else {
      myDoingBulkUpdate = false;
      getPublisher().updateFinished(this);
    }
  }

  @Nullable
  public EditorHighlighter getEditorHighlighterForCachesBuilding() {
    final WeakReference<EditorHighlighter> editorHighlighterWeakReference = getUserData(ourSomeEditorSyntaxHighlighter);
    final EditorHighlighter someEditorHighlighter = editorHighlighterWeakReference != null ? editorHighlighterWeakReference.get():null;

    if (someEditorHighlighter instanceof LexerEditorHighlighter) {
      return someEditorHighlighter;
    }
    return null;
  }

  public void rememberEditorHighlighterForCachesOptimization(@NotNull final EditorHighlighter highlighter) {
    putUserData(ourSomeEditorSyntaxHighlighter, new WeakReference<EditorHighlighter>(highlighter));
  }

  private static class DocumentBulkUpdateListenerHolder {
    private static final DocumentBulkUpdateListener ourBulkChangePublisher =
        ApplicationManager.getApplication().getMessageBus().syncPublisher(DocumentBulkUpdateListener.TOPIC);
  }

  private static DocumentBulkUpdateListener getPublisher() {
    return DocumentBulkUpdateListenerHolder.ourBulkChangePublisher;
  }
}

