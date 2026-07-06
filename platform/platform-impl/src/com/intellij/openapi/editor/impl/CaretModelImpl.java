// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.CaretActionListener;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.ElfCandidate;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@ElfCandidate
public final class CaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable, Dumpable, InlayModel.Listener {
  private static final RegistryValue MAX_CARET_COUNT = Registry.get("editor.max.caret.count");
  private static final RegistryValue PRIMARY_SELECTION_CARET_UPDATE = Registry.get("editor.caret.update.primary.selection");

  private final EditorImpl editor;
  private final DocumentEx document;
  private final EventDispatcher<CaretListener> caretListeners;
  private final EventDispatcher<CaretActionListener> caretActionListeners;
  private final RangeMarkerTree<CaretImpl.PositionMarker> positionMarkerTree;
  private final RangeMarkerTree<CaretImpl.SelectionMarker> selectionMarkerTree;
  private final ThreadLocal<CaretImpl> currentCaret = new ThreadLocal<>(); // active caret in the context of 'runForEachCaret' call
  private final LinkedList<CaretImpl> allCarets = new LinkedList<>();
  private volatile @NotNull CaretImpl primaryCaret;
  private boolean performCaretMergingAfterCurrentOperation;
  private boolean visualPositionUpdateScheduled;
  private boolean editorSizeValidationScheduled;
  private boolean documentInUpdate;
  private int documentUpdateCounter;
  private TextAttributes textAttributes;

  public CaretModelImpl(@NotNull EditorImpl editor) {
    this.editor = editor;
    this.document = editor.getElfDocument();
    this.caretListeners = EventDispatcher.create(CaretListener.class);
    this.caretActionListeners = EventDispatcher.create(CaretActionListener.class);
    this.positionMarkerTree = new RangeMarkerTree<>(document);
    this.selectionMarkerTree = new RangeMarkerTree<>(document);
    this.primaryCaret = new CaretImpl(editor, this);
    this.allCarets.add(primaryCaret);
    editor.addPropertyChangeListener(
      new CaretPropertyChangeListener(editor, allCarets),
      this
    );
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    caretListeners.addListener(listener);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    caretListeners.removeListener(listener);
  }

  @Override
  public @NotNull TextAttributes getTextAttributes() {
    TextAttributes textAttributes = this.textAttributes;
    if (textAttributes == null) {
      this.textAttributes = textAttributes = new TextAttributes();
      if (editor.getSettings().isCaretRowShown()) {
        textAttributes.setBackgroundColor(editor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
      }
    }
    return textAttributes;
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
    CaretImpl currentCaret = this.currentCaret.get();
    return currentCaret != null ? currentCaret : getPrimaryCaret();
  }

  @Override
  public @NotNull CaretImpl getPrimaryCaret() {
    return primaryCaret;
  }

  @Override
  public int getCaretCount() {
    synchronized (allCarets) {
      return allCarets.size();
    }
  }

  @Override
  public @NotNull List<Caret> getAllCarets() {
    List<Caret> carets;
    synchronized (allCarets) {
      carets = new ArrayList<>(allCarets);
    }
    carets.sort(CARET_POSITION_COMPARATOR);
    return carets;
  }

  @Override
  public @Nullable Caret getCaretAt(@NotNull VisualPosition pos) {
    synchronized (allCarets) {
      for (CaretImpl caret : allCarets) {
        if (caret.getVisualPosition().equals(pos)) {
          return caret;
        }
      }
      return null;
    }
  }

  @Override
  public @Nullable Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    ThreadingAssertions.assertEventDispatchThread();
    CaretImpl caret = new CaretImpl(editor, this);
    caret.doMoveToVisualPosition(pos, false);
    if (addCaret(caret, makePrimary)) {
      return caret;
    }
    Disposer.dispose(caret);
    return null;
  }

  @Override
  public @Nullable Caret addCaret(@NotNull LogicalPosition pos, boolean makePrimary) {
    ThreadingAssertions.assertEventDispatchThread();
    CaretImpl caret = new CaretImpl(editor, this);
    caret.moveToLogicalPosition(pos, false, null, false, false);
    if (addCaret(caret, makePrimary)) {
      return caret;
    }
    Disposer.dispose(caret);
    return null;
  }

  @Override
  public boolean removeCaret(@NotNull Caret caret) {
    ThreadingAssertions.assertEventDispatchThread();
    if (allCarets.size() <= 1 || !(caret instanceof CaretImpl)) {
      return false;
    }
    synchronized (allCarets) {
      if (!allCarets.remove(caret)) {
        return false;
      }
      primaryCaret = allCarets.getLast();
    }
    fireCaretRemoved(caret);
    Disposer.dispose(caret);
    return true;
  }

  @Override
  public void removeSecondaryCarets() {
    ThreadingAssertions.assertEventDispatchThread();
    ListIterator<CaretImpl> caretIterator = allCarets.listIterator(allCarets.size() - 1);
    while (caretIterator.hasPrevious()) {
      CaretImpl caret = caretIterator.previous();
      synchronized (allCarets) {
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
  public void runForEachCaret(@NotNull CaretAction action, boolean reverseOrder) {
    if (currentCaret.get() != null) {
      throw new IllegalStateException("Recursive runForEachCaret invocations are not allowed");
    }
    Runnable iteration = () -> {
      try {
        List<Caret> sortedCarets = getAllCarets();
        Iterable<Caret> caretIterable = reverseOrder ? ContainerUtil.iterateBackward(sortedCarets) : sortedCarets;
        for (Caret caret : caretIterable) {
          currentCaret.set((CaretImpl)caret);
          action.perform(caret);
        }
      } finally {
        currentCaret.remove();
      }
    };
    if (EDT.isCurrentThreadEdt()) {
      caretActionListeners.getMulticaster().beforeAllCaretsAction();
      try {
        doWithCaretMerging(iteration);
      } finally {
        caretActionListeners.getMulticaster().afterAllCaretsAction();
      }
    } else {
      iteration.run();
    }
  }

  @Override
  public void addCaretActionListener(@NotNull CaretActionListener listener, @NotNull Disposable disposable) {
    caretActionListeners.addListener(listener, disposable);
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    ThreadingAssertions.assertEventDispatchThread();
    doWithCaretMerging(runnable);
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates) {
    setCaretsAndSelections(caretStates, true);
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates, boolean updateSystemSelection) {
    ThreadingAssertions.assertEventDispatchThread();
    if (caretStates.isEmpty()) {
      throw new IllegalArgumentException("At least one caret should exist");
    }

    int maxCaretCount = getMaxCaretCount();
    List<? extends CaretState> states;
    if (caretStates.size() <= maxCaretCount) {
      states = caretStates;
    } else {
      states = caretStates.subList(0, maxCaretCount);
      EditorUtil.notifyMaxCarets(editor);
    }
    doWithCaretMerging(() -> {
      int index = 0;
      int oldCaretCount = allCarets.size();
      Iterator<CaretImpl> caretIterator = allCarets.iterator();
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
          caret = new CaretImpl(editor, this);
          if (caretState != null && caretState.getCaretPosition() != null) {
            caret.moveToLogicalPosition(caretState.getCaretPosition(), false, null, false, false);
          }
          synchronized (allCarets) {
            allCarets.add(caret);
            primaryCaret = caret;
          }
          fireCaretAdded(caret);
        }
        if (caretState != null && caretState.getCaretPosition() != null && caretState.getVisualColumnAdjustment() != 0) {
          caret.setVisualColumnAdjustment(caretState.getVisualColumnAdjustment());
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
          caret.doSetSelection(
            editor.logicalToVisualPosition(caretState.getSelectionStart()),
            editor.logicalPositionToOffset(caretState.getSelectionStart()),
            editor.logicalToVisualPosition(caretState.getSelectionEnd()),
            editor.logicalPositionToOffset(caretState.getSelectionEnd()),
            /* visualPositionAware */ true,
            /* updateSystemSelection */ false,
            /* fireListeners */ false
          );
          selectionStartsAfter.add(caret.getSelectionStart());
          selectionEndsAfter.add(caret.getSelectionEnd());
        }
      }
      int caretsToRemove = allCarets.size() - states.size();
      for (int i = 0; i < caretsToRemove; i++) {
        CaretImpl caret;
        synchronized (allCarets) {
          caret = allCarets.removeLast();
          primaryCaret = allCarets.getLast();
        }
        fireCaretRemoved(caret);
        Disposer.dispose(caret);
      }
      if (updateSystemSelection) {
        updateSystemSelection();
      }
      if (selectionStartsBefore != null) {
        SelectionEvent event = new SelectionEvent(
          editor,
          selectionStartsBefore.toIntArray(),
          selectionEndsBefore.toIntArray(),
          selectionStartsAfter.toIntArray(),
          selectionEndsAfter.toIntArray()
        );
        editor.getSelectionModel().fireSelectionChanged(event);
      }
    });
  }

  @Override
  public @NotNull List<CaretState> getCaretsAndSelections() {
    synchronized (allCarets) {
      List<CaretState> states = new ArrayList<>(allCarets.size());
      for (CaretImpl caret : allCarets) {
        states.add(caret.getCaretState());
      }
      return states;
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.CARET_MODEL;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent e) {
    if (!isInBulkUpdate() && e.isWholeTextReplaced()) {
      for (CaretImpl caret : allCarets) {
        // logical position will be needed to restore caret position via diff
        caret.updateCachedStateIfNeeded();
      }
    }
    documentInUpdate = true;
    visualPositionUpdateScheduled = false;
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent e) {
    documentInUpdate = false;
    documentUpdateCounter++;
    if (!isInBulkUpdate()) {
      doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
      if (visualPositionUpdateScheduled) {
        updateVisualPosition();
      }
    }
  }

  @Override
  public void dispose() {
    for (CaretImpl caret : allCarets) {
      Disposer.dispose(caret);
    }
    //noinspection SuspiciousPackagePrivateAccess
    selectionMarkerTree.dispose(document);
    //noinspection SuspiciousPackagePrivateAccess
    positionMarkerTree.dispose(document);
  }

  @Override
  public void onAdded(@NotNull Inlay<?> inlay) {
    if (isInBulkUpdate() || editor.getInlayModel().isInBatchMode()) {
      return;
    }
    Inlay.Placement placement = inlay.getPlacement();
    if (placement == Inlay.Placement.INLINE) {
      int offset = inlay.getOffset();
      for (CaretImpl caret : allCarets) {
        caret.onInlayAdded(offset);
      }
    } else if (placement != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onRemoved(@NotNull Inlay<?> inlay) {
    if (isInBulkUpdate() || editor.getInlayModel().isInBatchMode()) {
      return;
    }
    Inlay.Placement placement = inlay.getPlacement();
    if (document.isInEventsHandling()) {
      if (placement == Inlay.Placement.AFTER_LINE_END) {
        visualPositionUpdateScheduled = true;
      }
      return;
    }
    if (placement == Inlay.Placement.INLINE) {
      doWithCaretMerging(() -> {
        for (CaretImpl caret : allCarets) {
          caret.onInlayRemoved(inlay.getOffset(), ((InlineInlayImpl<?>)inlay).getOrder());
        }
      });
    } else if (placement != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    if (isInBulkUpdate() ||
        editor.getInlayModel().isInBatchMode() ||
        (changeFlags & (InlayModel.ChangeFlags.WIDTH_CHANGED | InlayModel.ChangeFlags.HEIGHT_CHANGED)) == 0) {
      return;
    }
    if (inlay.getPlacement() != Inlay.Placement.AFTER_LINE_END || hasCaretInVirtualSpace()) {
      updateVisualPosition();
    }
  }

  @Override
  public void onBatchModeFinish(@NotNull Editor editor) {
    WriteIntentReadAction.run(() -> {
      if (isInBulkUpdate()) return;
      doWithCaretMerging(() -> {
        for (CaretImpl caret : allCarets) {
          caret.resetCachedState();
          caret.updateVisualPosition();
        }
      });
    });
  }

  @Override
  public @NotNull String dumpState() {
    return "[in update: " + documentInUpdate +
           ", update counter: " + documentUpdateCounter +
           ", perform caret merging: " + performCaretMergingAfterCurrentOperation +
           ", current caret: " + currentCaret.get() +
           ", all carets: " + ContainerUtil.map(allCarets, CaretImpl::dumpState) + "]";
  }

  public void reinitSettings() {
    textAttributes = null;
  }

  public void updateVisualPosition() {
    for (CaretImpl caret : allCarets) {
      caret.updateVisualPosition();
    }
  }

  public boolean isIteratingOverCarets() {
    return currentCaret.get() != null;
  }

  void onBulkDocumentUpdateFinished() {
    doWithCaretMerging(() -> {}); // do caret merging if it's not scheduled for later
  }

  int getWordAtCaretStart(boolean camel) {
    return getCurrentCaret().getWordAtCaretStart(camel);
  }

  int getWordAtCaretEnd(boolean camel) {
    return getCurrentCaret().getWordAtCaretEnd(camel);
  }

  boolean addCaret(@NotNull CaretImpl caretToAdd, boolean makePrimary) {
    if (allCarets.size() >= getMaxCaretCount()) {
      return false;
    }
    for (CaretImpl caret : allCarets) {
      if (caretsOverlap(caret, caretToAdd)) {
        return false;
      }
    }
    synchronized (allCarets) {
      if (makePrimary) {
        allCarets.addLast(caretToAdd);
        primaryCaret = caretToAdd;
      } else {
        allCarets.addFirst(caretToAdd);
      }
    }
    fireCaretAdded(caretToAdd);
    return true;
  }

  void doWithCaretMerging(@NotNull Runnable runnable) {
    ThreadingAssertions.assertEventDispatchThread();
    if (performCaretMergingAfterCurrentOperation) {
      runnable.run();
    } else {
      performCaretMergingAfterCurrentOperation = true;
      try {
        runnable.run();
        mergeOverlappingCaretsAndSelections();
      } finally {
        performCaretMergingAfterCurrentOperation = false;
      }
    }
  }

  void updateSystemSelection() {
    if (GraphicsEnvironment.isHeadless() ||
        !PRIMARY_SELECTION_CARET_UPDATE.asBoolean() ||
        !CopyPasteManager.getInstance().isSystemSelectionSupported()) {
      return;
    }
    Transferable selection = new StringSelection(editor.getSelectionModel().getSelectedText(true));
    CopyPasteManager.getInstance().setSystemSelectionContents(selection);
  }

  void fireCaretPositionChanged(@NotNull CaretEvent caretEvent) {
    caretListeners.getMulticaster().caretPositionChanged(caretEvent);
  }

  void validateEditorSize() {
    if (editor.getSettings().isVirtualSpace()) {
      if (performCaretMergingAfterCurrentOperation) {
        editorSizeValidationScheduled = true;
      } else {
        editor.validateSize();
      }
    }
  }

  int getDocumentUpdateCounter() {
    return documentUpdateCounter;
  }

  boolean isDocumentInUpdate() {
    return documentInUpdate;
  }

  RangeMarkerTree<CaretImpl.PositionMarker> getPositionMarkerTree() {
    return positionMarkerTree;
  }

  RangeMarkerTree<CaretImpl.SelectionMarker> getSelectionMarkerTree() {
    return selectionMarkerTree;
  }

  private void mergeOverlappingCaretsAndSelections() {
    ThreadingAssertions.assertEventDispatchThread();
    if (allCarets.size() > 1) {
      LinkedList<CaretImpl> carets = new LinkedList<>(allCarets);
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
          if (currCaret.getOffset() >= prevCaret.getSelectionStart() &&
              currCaret.getOffset() <= prevCaret.getSelectionEnd()) {
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
        synchronized (allCarets) {
          allCarets.remove(keepPrimary);
          allCarets.add(keepPrimary);
          primaryCaret = keepPrimary;
        }
      }
    }
    if (editorSizeValidationScheduled) {
      editorSizeValidationScheduled = false;
      editor.validateSize();
    }
  }

  private void fireCaretAdded(@NotNull Caret caret) {
    CaretEvent caretEvent = new CaretEvent(caret, caret.getLogicalPosition(), caret.getLogicalPosition());
    caretListeners.getMulticaster().caretAdded(caretEvent);
  }

  private void fireCaretRemoved(@NotNull Caret caret) {
    CaretEvent caretEvent = new CaretEvent(caret, caret.getLogicalPosition(), caret.getLogicalPosition());
    caretListeners.getMulticaster().caretRemoved(caretEvent);
  }

  private boolean hasCaretInVirtualSpace() {
    return editor.getSettings().isVirtualSpace() && ContainerUtil.exists(allCarets, CaretImpl::isInVirtualSpace);
  }

  private boolean isInBulkUpdate() {
    return document.isInBulkUpdate();
  }

  @TestOnly
  public void validateState() {
    for (CaretImpl caret : allCarets) {
      caret.validateState();
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
    return (firstStart < secondStart && firstEnd > secondStart) ||
           (firstStart > secondStart && firstStart < secondEnd) ||
           (firstStart == secondStart && secondEnd != secondStart && firstEnd > firstStart) ||
           ((hasPureVirtualSelection(firstCaret) || hasPureVirtualSelection(secondCaret)) && (firstStart == secondStart || firstEnd == secondEnd));
  }

  private static boolean hasPureVirtualSelection(@NotNull CaretImpl firstCaret) {
    return firstCaret.getSelectionStart() == firstCaret.getSelectionEnd() && firstCaret.hasVirtualSelection();
  }

  private static final Comparator<Caret> CARET_POSITION_COMPARATOR = (caret1, caret2) -> {
    VisualPosition pos1 = caret1.getVisualPosition();
    VisualPosition pos2 = caret2.getVisualPosition();
    if (pos1.line == pos2.line) {
      return pos1.column - pos2.column;
    }
    return pos1.line - pos2.line;
  };
}
