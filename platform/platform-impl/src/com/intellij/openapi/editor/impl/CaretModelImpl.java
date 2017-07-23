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

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public class CaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable, Dumpable, InlayModel.Listener {
  private final EditorImpl myEditor;
  
  private final EventDispatcher<CaretListener> myCaretListeners = EventDispatcher.create(CaretListener.class);

  private TextAttributes myTextAttributes;

  boolean myIsInUpdate;

  final RangeMarkerTree<CaretImpl.PositionMarker> myPositionMarkerTree;
  final RangeMarkerTree<CaretImpl.SelectionMarker> mySelectionMarkerTree;

  private final LinkedList<CaretImpl> myCarets = new LinkedList<>();
  private CaretImpl myCurrentCaret; // active caret in the context of 'runForEachCaret' call
  private boolean myPerformCaretMergingAfterCurrentOperation;

  int myDocumentUpdateCounter;

  public CaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    myEditor.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (EditorEx.PROP_COLUMN_MODE.equals(evt.getPropertyName()) && !myEditor.isColumnMode()) {
          for (CaretImpl caret : myCarets) {
            caret.resetVirtualSelection();
          }
        }
      }
    }, this);

    myPositionMarkerTree = new RangeMarkerTree<>(myEditor.getDocument());
    mySelectionMarkerTree = new RangeMarkerTree<>(myEditor.getDocument());
  }

  void initCarets() {
    myCarets.add(new CaretImpl(myEditor));
  }

  void onBulkDocumentUpdateStarted() {
  }

  void onBulkDocumentUpdateFinished() {
    doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
  }

  @Override
  public void documentChanged(final DocumentEvent e) {
    myIsInUpdate = false;
    myDocumentUpdateCounter++;
    if (!myEditor.getDocument().isInBulkUpdate()) {
      doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
    }
  }

  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    myIsInUpdate = true;
    if (!myEditor.getDocument().isInBulkUpdate() && e.isWholeTextReplaced()) {
      for (CaretImpl caret : myCarets) {
        caret.updateCachedStateIfNeeded(); // logical position will be needed to restore caret position via diff
      }
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.CARET_MODEL;
  }

  @Override
  public void dispose() {
    for (CaretImpl caret : myCarets) {
      Disposer.dispose(caret);
    }
    mySelectionMarkerTree.dispose();
    myPositionMarkerTree.dispose();
  }

  public void updateVisualPosition() {
    for (CaretImpl caret : myCarets) {
      caret.updateVisualPosition();
    }
  }

  @Override
  public void moveCaretRelatively(final int columnShift, final int lineShift, final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    getCurrentCaret().moveCaretRelatively(columnShift, lineShift, withSelection, scrollToCaret);
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    getCurrentCaret().moveToLogicalPosition(pos);
  }

  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    getCurrentCaret().moveToVisualPosition(pos);
  }

  @Override
  public void moveToOffset(int offset) {
    getCurrentCaret().moveToOffset(offset);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    getCurrentCaret().moveToOffset(offset, locateBeforeSoftWrap);
  }

  @Override
  public boolean isUpToDate() {
    return getCurrentCaret().isUpToDate();
  }

  @NotNull
  @Override
  public LogicalPosition getLogicalPosition() {
    return getCurrentCaret().getLogicalPosition();
  }

  @NotNull
  @Override
  public VisualPosition getVisualPosition() {
    return getCurrentCaret().getVisualPosition();
  }

  @Override
  public int getOffset() {
    return getCurrentCaret().getOffset();
  }

  @Override
  public int getVisualLineStart() {
    return getCurrentCaret().getVisualLineStart();
  }

  @Override
  public int getVisualLineEnd() {
    return getCurrentCaret().getVisualLineEnd();
  }

  int getWordAtCaretStart() {
    return getCurrentCaret().getWordAtCaretStart();
  }

  int getWordAtCaretEnd() {
    return getCurrentCaret().getWordAtCaretEnd();
  }

  @Override
  public void addCaretListener(@NotNull final CaretListener listener) {
    myCaretListeners.addListener(listener);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    myCaretListeners.removeListener(listener);
  }

  @Override
  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      myTextAttributes = new TextAttributes();
      if (myEditor.getSettings().isCaretRowShown()) {
        myTextAttributes.setBackgroundColor(myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
      }
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }

  @Override
  public boolean supportsMultipleCarets() {
    return true;
  }

  @Override
  @NotNull
  public CaretImpl getCurrentCaret() {
    CaretImpl currentCaret = myCurrentCaret;
    return ApplicationManager.getApplication().isDispatchThread() && currentCaret != null ? currentCaret : getPrimaryCaret();
  }

  @Override
  @NotNull
  public CaretImpl getPrimaryCaret() {
    synchronized (myCarets) {
      return myCarets.get(myCarets.size() - 1);
    }
  }

  @Override
  public int getCaretCount() {
    synchronized (myCarets) {
      return myCarets.size();
    }
  }

  @Override
  @NotNull
  public List<Caret> getAllCarets() {
    List<Caret> carets;
    synchronized (myCarets) {
      carets = new ArrayList<>(myCarets);
    }
    Collections.sort(carets, CaretPositionComparator.INSTANCE);
    return carets;
  }

  @Nullable
  @Override
  public Caret getCaretAt(@NotNull VisualPosition pos) {
    synchronized (myCarets) {
      for (CaretImpl caret : myCarets) {
        if (caret.getVisualPosition().equals(pos)) {
          return caret;
        }
      }
      return null;
    }
  }

  @Nullable
  @Override
  public Caret addCaret(@NotNull VisualPosition pos) {
    return addCaret(pos, true);
  }

  @Nullable
  @Override
  public Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    EditorImpl.assertIsDispatchThread();
    CaretImpl caret = new CaretImpl(myEditor);
    caret.moveToVisualPosition(pos, false);
    if (addCaret(caret, makePrimary)) {
      return caret;
    }
    else {
      Disposer.dispose(caret);
      return null;
    }
  }

  boolean addCaret(@NotNull CaretImpl caretToAdd, boolean makePrimary) {
    for (CaretImpl caret : myCarets) {
      if (caretsOverlap(caret, caretToAdd)) {
        return false;
      }
    }
    synchronized (myCarets) {
      if (makePrimary) {
        myCarets.addLast(caretToAdd);
      }
      else {
        myCarets.addFirst(caretToAdd);
      }
    }
    fireCaretAdded(caretToAdd);
    return true;
  }

  @Override
  public boolean removeCaret(@NotNull Caret caret) {
    EditorImpl.assertIsDispatchThread();
    if (myCarets.size() <= 1 || !(caret instanceof CaretImpl)) {
      return false;
    }
    synchronized (myCarets) {
      if (!myCarets.remove(caret)) {
        return false;
      }
    }
    fireCaretRemoved(caret);
    Disposer.dispose(caret);
    return true;
  }

  @Override
  public void removeSecondaryCarets() {
    EditorImpl.assertIsDispatchThread();
    ListIterator<CaretImpl> caretIterator = myCarets.listIterator(myCarets.size() - 1);
    while (caretIterator.hasPrevious()) {
      CaretImpl caret = caretIterator.previous();
      synchronized (myCarets) {
        caretIterator.remove();
      }
      fireCaretRemoved(caret);
      Disposer.dispose(caret);
    }
  }

  @Override
  public void runForEachCaret(@NotNull final CaretAction action) {
    runForEachCaret(action, false);
  }

  @Override
  public void runForEachCaret(@NotNull final CaretAction action, final boolean reverseOrder) {
    EditorImpl.assertIsDispatchThread();
    if (myCurrentCaret != null) {
      throw new IllegalStateException("Recursive runForEachCaret invocations are not allowed");
    }
    doWithCaretMerging(() -> {
      try {
        List<Caret> sortedCarets = getAllCarets();
        if (reverseOrder) {
          Collections.reverse(sortedCarets);
        }
        for (Caret caret : sortedCarets) {
          myCurrentCaret = (CaretImpl)caret;
          action.perform(caret);
        }
      }
      finally {
        myCurrentCaret = null;
      }
    });
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    EditorImpl.assertIsDispatchThread();
    doWithCaretMerging(runnable);
  }

  private void mergeOverlappingCaretsAndSelections() {
    if (myCarets.size() <= 1) {
      return;
    }
    LinkedList<CaretImpl> carets = new LinkedList<>(myCarets);
    Collections.sort(carets, CaretPositionComparator.INSTANCE);
    ListIterator<CaretImpl> it = carets.listIterator();
    CaretImpl keepPrimary = getPrimaryCaret();
    while (it.hasNext()) {
      CaretImpl prevCaret = null;
      if (it.hasPrevious()) {
        prevCaret = it.previous();
        it.next();
      }
      CaretImpl currCaret = it.next();
      if (prevCaret != null && caretsOverlap(currCaret, prevCaret)) {
        int newSelectionStart = Math.min(currCaret.getSelectionStart(), prevCaret.getSelectionStart());
        int newSelectionEnd = Math.max(currCaret.getSelectionEnd(), prevCaret.getSelectionEnd());
        CaretImpl toRetain, toRemove;
        if (currCaret.getOffset() >= prevCaret.getSelectionStart() && currCaret.getOffset() <= prevCaret.getSelectionEnd()) {
          toRetain = prevCaret;
          toRemove = currCaret;
          it.remove();
          it.previous();
        }
        else {
          toRetain = currCaret;
          toRemove = prevCaret;
          it.previous();
          it.previous();
          it.remove();
        }
        if (toRemove == keepPrimary) {
          keepPrimary = toRetain;
        }
        removeCaret(toRemove);
        if (newSelectionStart < newSelectionEnd) {
          toRetain.setSelection(newSelectionStart, newSelectionEnd);
        }
      }
    }
    if (keepPrimary != getPrimaryCaret()) {
      synchronized (myCarets) {
        myCarets.remove(keepPrimary);
        myCarets.add(keepPrimary);
      }
    }
  }

  private static boolean caretsOverlap(@NotNull CaretImpl firstCaret, @NotNull CaretImpl secondCaret) {
    if (firstCaret.getVisualPosition().equals(secondCaret.getVisualPosition())) {
      return true;
    }
    int firstStart = firstCaret.getSelectionStart();
    int secondStart = secondCaret.getSelectionStart();
    int firstEnd = firstCaret.getSelectionEnd();
    int secondEnd = secondCaret.getSelectionEnd();
    return firstStart < secondStart && firstEnd > secondStart
      || firstStart > secondStart && firstStart < secondEnd
      || firstStart == secondStart && secondEnd != secondStart && firstEnd > firstStart
      || (hasPureVirtualSelection(firstCaret) || hasPureVirtualSelection(secondCaret)) && (firstStart == secondStart || firstEnd == secondEnd);
  }

  private static boolean hasPureVirtualSelection(CaretImpl firstCaret) {
    return firstCaret.getSelectionStart() == firstCaret.getSelectionEnd() && firstCaret.hasVirtualSelection();
  }

  void doWithCaretMerging(Runnable runnable) {
    if (myPerformCaretMergingAfterCurrentOperation) {
      runnable.run();
    }
    else {
      myPerformCaretMergingAfterCurrentOperation = true;
      try {
        runnable.run();
        mergeOverlappingCaretsAndSelections();
      }
      finally {
        myPerformCaretMergingAfterCurrentOperation = false;
      }
    }
  }

  @Override
  public void setCaretsAndSelections(@NotNull final List<CaretState> caretStates) {
    setCaretsAndSelections(caretStates, true);
  }

  @Override
  public void setCaretsAndSelections(@NotNull final List<CaretState> caretStates, final boolean updateSystemSelection) {
    EditorImpl.assertIsDispatchThread();
    if (caretStates.isEmpty()) {
      throw new IllegalArgumentException("At least one caret should exist");
    }
    doWithCaretMerging(() -> {
      int index = 0;
      int oldCaretCount = myCarets.size();
      Iterator<CaretImpl> caretIterator = myCarets.iterator();
      TIntArrayList selectionStartsBefore = null;
      TIntArrayList selectionStartsAfter = null;
      TIntArrayList selectionEndsBefore = null;
      TIntArrayList selectionEndsAfter = null;
      for (CaretState caretState : caretStates) {
        CaretImpl caret;
        if (index++ < oldCaretCount) {
          caret = caretIterator.next();
          if (caretState != null && caretState.getCaretPosition() != null) {
            caret.moveToLogicalPosition(caretState.getCaretPosition());
          }
        }
        else {
          caret = new CaretImpl(myEditor);
          if (caretState != null && caretState.getCaretPosition() != null) {
            caret.moveToLogicalPosition(caretState.getCaretPosition(), false, null, false);
          }
          synchronized (myCarets) {
            myCarets.add(caret);
          }
          fireCaretAdded(caret);
        }
        if (caretState != null && caretState.getCaretPosition() != null && caretState.getVisualColumnAdjustment() != 0) {
          caret.myVisualColumnAdjustment = caretState.getVisualColumnAdjustment();
          caret.updateVisualPosition();
        } 
        if (caretState != null && caretState.getSelectionStart() != null && caretState.getSelectionEnd() != null) {
          if (selectionStartsBefore == null) {
            int capacity = caretStates.size();
            selectionStartsBefore = new TIntArrayList(capacity);
            selectionStartsAfter = new TIntArrayList(capacity);
            selectionEndsBefore = new TIntArrayList(capacity);
            selectionEndsAfter = new TIntArrayList(capacity);
          }
          selectionStartsBefore.add(caret.getSelectionStart());
          selectionEndsBefore.add(caret.getSelectionEnd());
          caret.doSetSelection(myEditor.logicalToVisualPosition(caretState.getSelectionStart()),
                               myEditor.logicalPositionToOffset(caretState.getSelectionStart()),
                               myEditor.logicalToVisualPosition(caretState.getSelectionEnd()),
                               myEditor.logicalPositionToOffset(caretState.getSelectionEnd()), 
                               true, updateSystemSelection, false);
          selectionStartsAfter.add(caret.getSelectionStart());
          selectionEndsAfter.add(caret.getSelectionEnd());
        }
      }
      int caretsToRemove = myCarets.size() - caretStates.size();
      for (int i = 0; i < caretsToRemove; i++) {
        CaretImpl caret;
        synchronized (myCarets) {
          caret = myCarets.removeLast();
        }
        fireCaretRemoved(caret);
        Disposer.dispose(caret);
      }
      if (selectionStartsBefore != null) {
        SelectionEvent event = new SelectionEvent(myEditor, selectionStartsBefore.toNativeArray(), selectionEndsBefore.toNativeArray(), 
                                                  selectionStartsAfter.toNativeArray(), selectionEndsAfter.toNativeArray());
        myEditor.getSelectionModel().fireSelectionChanged(event);
      }
    });
  }

  @NotNull
  @Override
  public List<CaretState> getCaretsAndSelections() {
    synchronized (myCarets) {
      List<CaretState> states = new ArrayList<>(myCarets.size());
      for (CaretImpl caret : myCarets) {
        states.add(new CaretState(caret.getLogicalPosition(),
                                  caret.myVisualColumnAdjustment,
                                  caret.getSelectionStartLogicalPosition(),
                                  caret.getSelectionEndLogicalPosition()));
      }
      return states;
    }
  }

  void fireCaretPositionChanged(CaretEvent caretEvent) {
    myCaretListeners.getMulticaster().caretPositionChanged(caretEvent);
  }

  void fireCaretAdded(@NotNull Caret caret) {
    myCaretListeners.getMulticaster().caretAdded(new CaretEvent(myEditor, caret, caret.getLogicalPosition(), caret.getLogicalPosition()));
  }

  void fireCaretRemoved(@NotNull Caret caret) {
    myCaretListeners.getMulticaster().caretRemoved(new CaretEvent(myEditor, caret, caret.getLogicalPosition(), caret.getLogicalPosition()));
  }

  public boolean isIteratingOverCarets() {
    return myCurrentCaret != null;
  }

  @NotNull
  @Override
  public String dumpState() {
    return "[in update: " + myIsInUpdate +
           ", update counter: " + myDocumentUpdateCounter +
           ", perform caret merging: " + myPerformCaretMergingAfterCurrentOperation +
           ", current caret: " + myCurrentCaret +
           ", all carets: " + ContainerUtil.map(myCarets, CaretImpl::dumpState) + "]";
  }

  @Override
  public void onAdded(@NotNull Inlay inlay) {
    if (myEditor.getDocument().isInBulkUpdate()) return;
    int offset = inlay.getOffset();
    for (CaretImpl caret : myCarets) {
      caret.onInlayAdded(offset);
    }
  }

  @Override
  public void onRemoved(@NotNull Inlay inlay) {
    if (myEditor.getDocument().isInEventsHandling() || myEditor.getDocument().isInBulkUpdate()) return;
    doWithCaretMerging(() -> {
      for (CaretImpl caret : myCarets) {
        caret.onInlayRemoved(inlay.getOffset(), ((InlayImpl)inlay).getOrder());
      }
    });
  }

  @Override
  public void onUpdated(@NotNull Inlay inlay) {
    if (myEditor.getDocument().isInBulkUpdate()) return;
    updateVisualPosition();
  }

  @TestOnly
  public void validateState() {
    for (CaretImpl caret : myCarets) {
      caret.validateState();
    }
  }

  private static class VisualPositionComparator implements Comparator<VisualPosition> {
    private static final VisualPositionComparator INSTANCE = new VisualPositionComparator();

    @Override
    public int compare(VisualPosition o1, VisualPosition o2) {
      if (o1.line != o2.line) {
        return o1.line - o2.line;
      }
      return o1.column - o2.column;
    }
  }

  private static class CaretPositionComparator implements Comparator<Caret> {
    private static final CaretPositionComparator INSTANCE = new CaretPositionComparator();

    @Override
    public int compare(Caret o1, Caret o2) {
      return VisualPositionComparator.INSTANCE.compare(o1.getVisualPosition(), o2.getVisualPosition());
    }
  }
}
