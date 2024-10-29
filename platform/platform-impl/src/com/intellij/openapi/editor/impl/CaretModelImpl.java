// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyClipboardOwner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.*;

public final class CaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable, Dumpable, InlayModel.Listener {
  private static final RegistryValue MAX_CARET_COUNT = Registry.get("editor.max.caret.count");

  private final EditorImpl myEditor;

  private final EventDispatcher<CaretListener> myCaretListeners = EventDispatcher.create(CaretListener.class);
  private final EventDispatcher<CaretActionListener> myCaretActionListeners = EventDispatcher.create(CaretActionListener.class);

  private TextAttributes myTextAttributes;

  boolean myIsInUpdate;

  final RangeMarkerTree<CaretImpl.PositionMarker> myPositionMarkerTree;
  final RangeMarkerTree<CaretImpl.SelectionMarker> mySelectionMarkerTree;

  private final LinkedList<CaretImpl> myCarets = new LinkedList<>();
  private volatile @NotNull CaretImpl myPrimaryCaret;
  private final ThreadLocal<CaretImpl> myCurrentCaret = new ThreadLocal<>(); // active caret in the context of 'runForEachCaret' call
  private boolean myPerformCaretMergingAfterCurrentOperation;
  private boolean myVisualPositionUpdateScheduled;
  private boolean myEditorSizeValidationScheduled;

  int myDocumentUpdateCounter;

  public CaretModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myEditor.addPropertyChangeListener(evt -> {
      if (EditorEx.PROP_COLUMN_MODE.equals(evt.getPropertyName()) && !myEditor.isColumnMode()) {
        for (CaretImpl caret : myCarets) {
          caret.resetVirtualSelection();
        }
      }
    }, this);

    myPositionMarkerTree = new RangeMarkerTree<>(myEditor.getDocument());
    mySelectionMarkerTree = new RangeMarkerTree<>(myEditor.getDocument());
    myPrimaryCaret = new CaretImpl(myEditor, this);
    myCarets.add(myPrimaryCaret);
  }

  void onBulkDocumentUpdateStarted() {
  }

  void onBulkDocumentUpdateFinished() {
    doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
  }

  @Override
  public void documentChanged(final @NotNull DocumentEvent e) {
    myIsInUpdate = false;
    myDocumentUpdateCounter++;
    if (!myEditor.getDocument().isInBulkUpdate()) {
      doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
      if (myVisualPositionUpdateScheduled) updateVisualPosition();
    }
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent e) {
    if (!myEditor.getDocument().isInBulkUpdate() && e.isWholeTextReplaced()) {
      for (CaretImpl caret : myCarets) {
        caret.updateCachedStateIfNeeded(); // logical position will be needed to restore caret position via diff
      }
    }
    myIsInUpdate = true;
    myVisualPositionUpdateScheduled = false;
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
    mySelectionMarkerTree.dispose(myEditor.getDocument());
    myPositionMarkerTree.dispose(myEditor.getDocument());
  }

  public void updateVisualPosition() {
    for (CaretImpl caret : myCarets) {
      caret.updateVisualPosition();
    }
  }

  int getWordAtCaretStart(boolean camel) {
    return getCurrentCaret().getWordAtCaretStart(camel);
  }

  int getWordAtCaretEnd(boolean camel) {
    return getCurrentCaret().getWordAtCaretEnd(camel);
  }

  @Override
  public void addCaretListener(final @NotNull CaretListener listener) {
    myCaretListeners.addListener(listener);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    myCaretListeners.removeListener(listener);
  }

  @Override
  public @NotNull TextAttributes getTextAttributes() {
    TextAttributes textAttributes = myTextAttributes;
    if (textAttributes == null) {
      myTextAttributes = textAttributes = new TextAttributes();
      if (myEditor.getSettings().isCaretRowShown()) {
        textAttributes.setBackgroundColor(myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
      }
    }

    return textAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }

  @Override
  public boolean supportsMultipleCarets() {
    return true;
  }

  @Override
  public int getMaxCaretCount() {
    return Math.max(1, MAX_CARET_COUNT.asInteger());
  }

  @Override
  public @NotNull CaretImpl getCurrentCaret() {
    CaretImpl currentCaret = myCurrentCaret.get();
    return currentCaret != null ? currentCaret : getPrimaryCaret();
  }

  @Override
  public @NotNull CaretImpl getPrimaryCaret() {
    return myPrimaryCaret;
  }

  @Override
  public int getCaretCount() {
    synchronized (myCarets) {
      return myCarets.size();
    }
  }

  @Override
  public @NotNull List<Caret> getAllCarets() {
    List<Caret> carets;
    synchronized (myCarets) {
      carets = new ArrayList<>(myCarets);
    }
    carets.sort(CARET_POSITION_COMPARATOR);
    return carets;
  }

  @Override
  public @Nullable Caret getCaretAt(@NotNull VisualPosition pos) {
    synchronized (myCarets) {
      for (CaretImpl caret : myCarets) {
        if (caret.getVisualPosition().equals(pos)) {
          return caret;
        }
      }
      return null;
    }
  }

  @Override
  public @Nullable Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    EditorImpl.assertIsDispatchThread();
    CaretImpl caret = new CaretImpl(myEditor, this);
    caret.doMoveToVisualPosition(pos, false);
    if (addCaret(caret, makePrimary)) {
      return caret;
    }
    Disposer.dispose(caret);
    return null;
  }

  @Override
  public @Nullable Caret addCaret(@NotNull LogicalPosition pos, boolean makePrimary) {
    EditorImpl.assertIsDispatchThread();
    CaretImpl caret = new CaretImpl(myEditor, this);
    caret.moveToLogicalPosition(pos, false, null, false, false);
    if (addCaret(caret, makePrimary)) {
      return caret;
    }
    Disposer.dispose(caret);
    return null;
  }

  boolean addCaret(@NotNull CaretImpl caretToAdd, boolean makePrimary) {
    if (myCarets.size() >= getMaxCaretCount()) {
      return false;
    }
    for (CaretImpl caret : myCarets) {
      if (caretsOverlap(caret, caretToAdd)) {
        return false;
      }
    }
    synchronized (myCarets) {
      if (makePrimary) {
        myCarets.addLast(caretToAdd);
        myPrimaryCaret = caretToAdd;
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
      myPrimaryCaret = myCarets.getLast();
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
  public void runForEachCaret(final @NotNull CaretAction action) {
    runForEachCaret(action, false);
  }

  @Override
  public void runForEachCaret(final @NotNull CaretAction action, final boolean reverseOrder) {
    if (myCurrentCaret.get() != null) {
      throw new IllegalStateException("Recursive runForEachCaret invocations are not allowed");
    }
    Runnable iteration = () -> {
      try {
        List<Caret> sortedCarets = getAllCarets();
        Iterable<Caret> caretIterable = reverseOrder ? ContainerUtil.iterateBackward(sortedCarets) : sortedCarets;
        for (Caret caret : caretIterable) {
          myCurrentCaret.set((CaretImpl)caret);
          action.perform(caret);
        }
      }
      finally {
        myCurrentCaret.set(null);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      myCaretActionListeners.getMulticaster().beforeAllCaretsAction();
      doWithCaretMerging(iteration);
      myCaretActionListeners.getMulticaster().afterAllCaretsAction();
    }
    else {
      iteration.run();
    }
  }

  @Override
  public void addCaretActionListener(@NotNull CaretActionListener listener, @NotNull Disposable disposable) {
    myCaretActionListeners.addListener(listener, disposable);
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    EditorImpl.assertIsDispatchThread();
    doWithCaretMerging(runnable);
  }

  private void mergeOverlappingCaretsAndSelections() {
    EditorImpl.assertIsDispatchThread();
    if (myCarets.size() > 1) {
      LinkedList<CaretImpl> carets = new LinkedList<>(myCarets);
      carets.sort(CARET_POSITION_COMPARATOR);
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
          CaretImpl toRetain;
          CaretImpl toRemove;
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
          myPrimaryCaret = keepPrimary;
        }
      }
    }
    if (myEditorSizeValidationScheduled) {
      myEditorSizeValidationScheduled = false;
      myEditor.validateSize();
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

  private static boolean hasPureVirtualSelection(@NotNull CaretImpl firstCaret) {
    return firstCaret.getSelectionStart() == firstCaret.getSelectionEnd() && firstCaret.hasVirtualSelection();
  }

  void doWithCaretMerging(@NotNull Runnable runnable) {
    EditorImpl.assertIsDispatchThread();
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
  public void setCaretsAndSelections(final @NotNull List<? extends CaretState> caretStates) {
    setCaretsAndSelections(caretStates, true);
  }

  @Override
  @RequiresEdt
  public void setCaretsAndSelections(final @NotNull List<? extends CaretState> caretStates, final boolean updateSystemSelection) {
    if (caretStates.isEmpty()) {
      throw new IllegalArgumentException("At least one caret should exist");
    }

    int maxCaretCount = getMaxCaretCount();
    List<? extends CaretState> states;
    if (caretStates.size() <= maxCaretCount) {
      states = caretStates;
    } else {
      states = caretStates.subList(0, maxCaretCount);
      EditorUtil.notifyMaxCarets(myEditor);
    }
    doWithCaretMerging(() -> {
      int index = 0;
      int oldCaretCount = myCarets.size();
      Iterator<CaretImpl> caretIterator = myCarets.iterator();
      IntCollection selectionStartsBefore = null;
      IntCollection selectionStartsAfter = null;
      IntCollection selectionEndsBefore = null;
      IntCollection selectionEndsAfter = null;
      for (CaretState caretState : states) {
        CaretImpl caret;
        if (index++ < oldCaretCount) {
          caret = caretIterator.next();
          if (caretState != null && caretState.getCaretPosition() != null) {
            caret.moveToLogicalPosition(caretState.getCaretPosition());
          }
        }
        else {
          caret = new CaretImpl(myEditor, this);
          if (caretState != null && caretState.getCaretPosition() != null) {
            caret.moveToLogicalPosition(caretState.getCaretPosition(), false, null, false, false);
          }
          synchronized (myCarets) {
            myCarets.add(caret);
            myPrimaryCaret = caret;
          }
          fireCaretAdded(caret);
        }
        if (caretState != null && caretState.getCaretPosition() != null && caretState.getVisualColumnAdjustment() != 0) {
          caret.myVisualColumnAdjustment = caretState.getVisualColumnAdjustment();
          caret.updateVisualPosition();
        }
        if (caretState != null && caretState.getSelectionStart() != null && caretState.getSelectionEnd() != null) {
          if (selectionStartsBefore == null) {
            int capacity = states.size();
            selectionStartsBefore = new IntArrayList(capacity);
            selectionStartsAfter = new IntArrayList(capacity);
            selectionEndsBefore = new IntArrayList(capacity);
            selectionEndsAfter = new IntArrayList(capacity);
          }
          selectionStartsBefore.add(caret.getSelectionStart());
          selectionEndsBefore.add(caret.getSelectionEnd());
          caret.doSetSelection(myEditor.logicalToVisualPosition(caretState.getSelectionStart()),
                               myEditor.logicalPositionToOffset(caretState.getSelectionStart()),
                               myEditor.logicalToVisualPosition(caretState.getSelectionEnd()),
                               myEditor.logicalPositionToOffset(caretState.getSelectionEnd()),
                               true, false, false);
          selectionStartsAfter.add(caret.getSelectionStart());
          selectionEndsAfter.add(caret.getSelectionEnd());
        }
      }
      int caretsToRemove = myCarets.size() - states.size();
      for (int i = 0; i < caretsToRemove; i++) {
        CaretImpl caret;
        synchronized (myCarets) {
          caret = myCarets.removeLast();
          myPrimaryCaret = myCarets.getLast();
        }
        fireCaretRemoved(caret);
        Disposer.dispose(caret);
      }
      if (updateSystemSelection) {
        updateSystemSelection();
      }
      if (selectionStartsBefore != null) {
        SelectionEvent event = new SelectionEvent(myEditor, selectionStartsBefore.toIntArray(), selectionEndsBefore.toIntArray(),
                                                  selectionStartsAfter.toIntArray(), selectionEndsAfter.toIntArray());
        myEditor.getSelectionModel().fireSelectionChanged(event);
      }
    });
  }

  @Override
  public @NotNull List<CaretState> getCaretsAndSelections() {
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

  void updateSystemSelection() {
    if (GraphicsEnvironment.isHeadless() || !Registry.is("editor.caret.update.primary.selection")) return;

    final Clipboard clip = myEditor.getComponent().getToolkit().getSystemSelection();
    if (clip != null) {
      clip.setContents(new StringSelection(myEditor.getSelectionModel().getSelectedText(true)), EmptyClipboardOwner.INSTANCE);
    }
  }

  void fireCaretPositionChanged(@NotNull CaretEvent caretEvent) {
    myCaretListeners.getMulticaster().caretPositionChanged(caretEvent);
  }

  void validateEditorSize() {
    if (myEditor.getSettings().isVirtualSpace()) {
      if (myPerformCaretMergingAfterCurrentOperation) {
        myEditorSizeValidationScheduled = true;
      }
      else {
        myEditor.validateSize();
      }
    }
  }

  private void fireCaretAdded(@NotNull Caret caret) {
    myCaretListeners.getMulticaster().caretAdded(new CaretEvent(caret, caret.getLogicalPosition(), caret.getLogicalPosition()));
  }

  private void fireCaretRemoved(@NotNull Caret caret) {
    myCaretListeners.getMulticaster().caretRemoved(new CaretEvent(caret, caret.getLogicalPosition(), caret.getLogicalPosition()));
  }

  public boolean isIteratingOverCarets() {
    return myCurrentCaret.get() != null;
  }

  @Override
  public @NotNull String dumpState() {
    return "[in update: " + myIsInUpdate +
           ", update counter: " + myDocumentUpdateCounter +
           ", perform caret merging: " + myPerformCaretMergingAfterCurrentOperation +
           ", current caret: " + myCurrentCaret.get() +
           ", all carets: " + ContainerUtil.map(myCarets, CaretImpl::dumpState) + "]";
  }

  @Override
  public void onAdded(@NotNull Inlay<?> inlay) {
    if (myEditor.getDocument().isInBulkUpdate() || myEditor.getInlayModel().isInBatchMode()) return;
    Inlay.Placement placement = inlay.getPlacement();
    if (placement == Inlay.Placement.INLINE) {
      int offset = inlay.getOffset();
      for (CaretImpl caret : myCarets) {
        caret.onInlayAdded(offset);
      }
    }
    else if (placement != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onRemoved(@NotNull Inlay<?> inlay) {
    if (myEditor.getDocument().isInBulkUpdate() || myEditor.getInlayModel().isInBatchMode()) return;
    Inlay.Placement placement = inlay.getPlacement();
    if (myEditor.getDocument().isInEventsHandling()) {
      if (placement == Inlay.Placement.AFTER_LINE_END) myVisualPositionUpdateScheduled = true;
      return;
    }
    if (placement == Inlay.Placement.INLINE) {
      doWithCaretMerging(() -> {
        for (CaretImpl caret : myCarets) {
          caret.onInlayRemoved(inlay.getOffset(), ((InlineInlayImpl<?>)inlay).getOrder());
        }
      });
    }
    else if (placement != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    if (myEditor.getDocument().isInBulkUpdate() ||
        myEditor.getInlayModel().isInBatchMode() ||
        (changeFlags & (InlayModel.ChangeFlags.WIDTH_CHANGED | InlayModel.ChangeFlags.HEIGHT_CHANGED)) == 0) {
      return;
    }
    if (inlay.getPlacement() != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onBatchModeFinish(@NotNull Editor editor) {
    WriteIntentReadAction.run((Runnable)() -> {
      if (myEditor.getDocument().isInBulkUpdate()) return;
      doWithCaretMerging(() -> {
        for (CaretImpl caret : myCarets) {
          caret.resetCachedState();
          caret.myVisualColumnAdjustment = 0;
          caret.updateVisualPosition();
        }
      });
    });
  }

  private boolean hasCaretInVirtualSpace() {
    return myEditor.getSettings().isVirtualSpace() && ContainerUtil.exists(myCarets, CaretImpl::isInVirtualSpace);
  }

  @TestOnly
  public void validateState() {
    for (CaretImpl caret : myCarets) {
      caret.validateState();
    }
  }

  private static final Comparator<VisualPosition> VISUAL_POSITION_COMPARATOR = (o1, o2) -> {
    if (o1.line != o2.line) {
      return o1.line - o2.line;
    }
    return o1.column - o2.column;
  };

  private static final Comparator<Caret> CARET_POSITION_COMPARATOR =
    (o1, o2) -> VISUAL_POSITION_COMPARATOR.compare(o1.getVisualPosition(), o2.getVisualPosition());
}
