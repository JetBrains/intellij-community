// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

public final class CommandMerger {
  private final UndoManagerImpl myManager;
  private final UndoManagerImpl.ClientState myState;
  private Reference<Object> myLastGroupId; // weak reference to avoid memleaks when clients pass some exotic objects as commandId
  private boolean myForcedGlobal;
  private boolean myTransparent;
  private @NlsContexts.Command String myCommandName;
  private boolean myValid = true;
  @NotNull
  private List<UndoableAction> myCurrentActions = new ArrayList<>();
  @NotNull
  private Set<DocumentReference> myAllAffectedDocuments = new HashSet<>();
  @NotNull
  private Set<DocumentReference> myAdditionalAffectedDocuments = new HashSet<>();
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private UndoConfirmationPolicy myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

  CommandMerger(@NotNull UndoManagerImpl.ClientState state) {
    myManager = state.myManager;
    myState = state;
  }

  CommandMerger(@NotNull UndoManagerImpl.ClientState state, boolean isTransparent) {
    myManager = state.myManager;
    myState = state;
    myTransparent = isTransparent;
  }

  public String getCommandName() {
    return myCommandName;
  }

  void addAction(@NotNull UndoableAction action) {
    myCurrentActions.add(action);
    addActionToSharedStack(action);
    DocumentReference[] refs = action.getAffectedDocuments();
    if (refs != null) {
      Collections.addAll(myAllAffectedDocuments, refs);
    }
    myForcedGlobal |= action.isGlobal();
  }

  private void addActionToSharedStack(@NotNull UndoableAction action) {
    if (action instanceof AdjustableUndoableAction adjustable) {
      DocumentReference[] affected = action.getAffectedDocuments();
      if (affected == null) {
        return;
      }
      for (DocumentReference reference : affected) {
        for (ActionChangeRange changeRange : adjustable.getChangeRanges(reference)) {
          myManager.getSharedUndoStacksHolder().addToStack(reference, changeRange);
          myManager.getSharedRedoStacksHolder().addToStack(reference, changeRange.createIndependentCopy(true));
        }
      }
    }
  }

  void commandFinished(@NlsContexts.Command String commandName, Object groupId, @NotNull CommandMerger nextCommandToMerge) {
    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    if (!nextCommandToMerge.isTransparent() && nextCommandToMerge.hasActions()) {
      clearRedoStacks(nextCommandToMerge);
    }

    if (!shouldMerge(groupId, nextCommandToMerge)) {
      flushCurrentCommand();
      myManager.compact(myState);
    }
    merge(nextCommandToMerge);

    if (nextCommandToMerge.isTransparent() || !hasActions()) return;

    if (groupId != SoftReference.dereference(myLastGroupId)) {
      myLastGroupId = groupId == null ? null : new WeakReference<>(groupId);
    }
    if (myCommandName == null) myCommandName = commandName;
  }

  private boolean shouldMerge(Object groupId, @NotNull CommandMerger nextCommandToMerge) {
    if (nextCommandToMerge.isTransparent() && nextCommandToMerge.myStateAfter == null && myStateAfter != null) return false;
    if (isTransparent() && myStateBefore == null && nextCommandToMerge.myStateBefore != null) return false;
    if (isTransparent() || nextCommandToMerge.isTransparent()) {
      return !hasActions() || !nextCommandToMerge.hasActions() || myAllAffectedDocuments.equals(nextCommandToMerge.myAllAffectedDocuments);
    }

    if ((myForcedGlobal || nextCommandToMerge.myForcedGlobal) && !isMergeGlobalCommandsAllowed()) return false;
    return canMergeGroup(groupId, SoftReference.dereference(myLastGroupId));
  }

  private static boolean isMergeGlobalCommandsAllowed() {
    return ((CoreCommandProcessor)CommandProcessor.getInstance()).isMergeGlobalCommandsAllowed();
  }

  // remove all references to document to avoid memory leaks
  void clearDocumentReferences(@NotNull Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    // DocumentReference for document is not equal to the DocumentReference from the file of that doc, so try both
    DocumentReference refByFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference refByDoc = new DocumentReferenceByDocument(document);
    myCurrentActions.removeIf(action -> {
      // remove UndoAction only if it doesn't contain anything but `document`, to avoid messing up with (very rare) complex undo actions containing several documents
      DocumentReference[] refs = ObjectUtils.notNull(action.getAffectedDocuments(), DocumentReference.EMPTY_ARRAY);
      return ContainerUtil.and(refs, ref -> ref.equals(refByDoc) || ref.equals(refByFile));
    });
    myAllAffectedDocuments.remove(refByFile);
    myAllAffectedDocuments.remove(refByDoc);
    myAdditionalAffectedDocuments.remove(refByFile);
    myAdditionalAffectedDocuments.remove(refByDoc);
  }

  private static boolean refMatch(@NotNull Document document, VirtualFile file, @NotNull DocumentReference ref) {
    return file != null && file.equals(ref.getFile()) || ref.getDocument() == document;
  }

  public static boolean canMergeGroup(Object groupId, Object lastGroupId) {
    return groupId != null && Comparing.equal(lastGroupId, groupId);
  }

  private void merge(@NotNull CommandMerger nextCommandToMerge) {
    setBeforeState(nextCommandToMerge.myStateBefore);
    myStateAfter = nextCommandToMerge.myStateAfter;
    if (myTransparent) { // todo write test
      if (nextCommandToMerge.hasActions()) {
        myTransparent = nextCommandToMerge.myTransparent;
      }
    }
    else {
      if (!hasActions()) {
        myTransparent = nextCommandToMerge.myTransparent;
      }
    }
    myValid &= nextCommandToMerge.myValid;
    myForcedGlobal |= nextCommandToMerge.myForcedGlobal;
    myCurrentActions.addAll(nextCommandToMerge.myCurrentActions);
    myAllAffectedDocuments.addAll(nextCommandToMerge.myAllAffectedDocuments);
    myAdditionalAffectedDocuments.addAll(nextCommandToMerge.myAdditionalAffectedDocuments);
    mergeUndoConfirmationPolicy(nextCommandToMerge.getUndoConfirmationPolicy());
  }

  void mergeUndoConfirmationPolicy(UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      myUndoConfirmationPolicy = undoConfirmationPolicy;
    }
    else if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      if (undoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
        myUndoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }
  }

  private static class MyEmptyUndoableAction extends BasicUndoableAction {
    MyEmptyUndoableAction(DocumentReference @NotNull [] refs) {
      super(refs);
    }

    @Override
    public void undo() throws UnexpectedUndoException {

    }

    @Override
    public void redo() throws UnexpectedUndoException {

    }
  }

  void flushCurrentCommand() {
    flushCurrentCommand(myState.nextCommandTimestamp(), myState.myUndoStacksHolder);
  }

  void flushCurrentCommand(int commandTimestamp, @NotNull UndoRedoStacksHolder stacksHolder) {
    if (hasActions()) {
      if (!myAdditionalAffectedDocuments.isEmpty()) {
        DocumentReference[] refs = myAdditionalAffectedDocuments.toArray(DocumentReference.EMPTY_ARRAY);
        myCurrentActions.add(new MyEmptyUndoableAction(refs));
      }

      stacksHolder.addToStacks(new UndoableGroup(myCommandName,
                                                 isGlobal(),
                                                 commandTimestamp,
                                                 myStateBefore,
                                                 myStateAfter,
                                                 myCurrentActions,
                                                 stacksHolder,
                                                 myManager.getProject(),
                                                 myUndoConfirmationPolicy,
                                                 isTransparent(),
                                                 myValid));
    }

    reset();
  }

  private void reset() {
    myCurrentActions = new ArrayList<>();
    myAllAffectedDocuments = new HashSet<>();
    myAdditionalAffectedDocuments = new HashSet<>();
    myLastGroupId = null;
    myForcedGlobal = false;
    myTransparent = false;
    myCommandName = null;
    myValid = true;
    myStateAfter = null;
    myStateBefore = null;
    myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;
  }

  private void clearRedoStacks(@NotNull CommandMerger nextMerger) {
    myState.myRedoStacksHolder.clearStacks(nextMerger.isGlobal(), nextMerger.myAllAffectedDocuments);
  }

  boolean isGlobal() {
    return myForcedGlobal || affectsMultiplePhysicalDocs();
  }

  void markAsGlobal() {
    myForcedGlobal = true;
  }

  boolean isTransparent() {
    return myTransparent;
  }

  private boolean affectsMultiplePhysicalDocs() {
    Set<VirtualFile> affectedFiles = new HashSet<>();
    for (DocumentReference each : myAllAffectedDocuments) {
      VirtualFile file = each.getFile();
      if (isVirtualDocumentChange(file)) continue;
      affectedFiles.add(file);
      if (affectedFiles.size() > 1) return true;
    }
    return false;
  }

  private static boolean isVirtualDocumentChange(VirtualFile file) {
    return file == null || file instanceof LightVirtualFile;
  }

  void undoOrRedo(FileEditor editor, boolean isUndo) {
    flushCurrentCommand();

    // here we _undo_ (regardless 'isUndo' flag) and drop all 'transparent' actions made right after undoRedo/redo.
    // Such actions should not get into redo/undoRedo stacks.  Note that 'transparent' actions that have been merged with normal actions
    // are not dropped, since this means they did not occur after undo/redo
    UndoRedo undoRedo;
    while ((undoRedo = createUndoOrRedo(editor, true)) != null) {
      if (!undoRedo.isTemporary()) break;
      if (!undoRedo.execute(true, false)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (!undoRedo.isTransparent()) break;
      if (!undoRedo.execute(false, false)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    boolean isInsideStartFinishGroup = false;
    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (editor != null && undoRedo.isBlockedByOtherChanges()) {
        UndoRedo blockingChange = createUndoOrRedo(null, isUndo);
        if (blockingChange != null && blockingChange.myUndoableGroup != undoRedo.myUndoableGroup) {
          if (undoRedo.confirmSwitchTo(blockingChange)) blockingChange.execute(false, true);
          break;
        }

        // if undo is block by other global command, trying to split global command and undo only local change in editor
        if (isUndo && undoRedo.myUndoableGroup.isGlobal() && Registry.is("ide.undo.fallback")) {
          if (myManager.splitGlobalCommand(undoRedo)) {
            var splittedUndo = createUndoOrRedo(editor, true);
            if (splittedUndo != null) undoRedo = splittedUndo;
          }
        }
      }
      if (!undoRedo.execute(false, isInsideStartFinishGroup)) return;

      if(editor != null && !isUndo && Registry.is("ide.undo.fallback")){
        myManager.gatherGlobalCommand(undoRedo);
      }

      isInsideStartFinishGroup = undoRedo.myUndoableGroup.isInsideStartFinishGroup(isUndo, isInsideStartFinishGroup);
      if (isInsideStartFinishGroup) continue;
      boolean shouldRepeat = undoRedo.isTransparent() && undoRedo.hasMoreActions();
      if (!shouldRepeat) break;
    }
  }

  @Nullable
  private UndoRedo createUndoOrRedo(FileEditor editor, boolean isUndo) {
    if (!myManager.isUndoOrRedoAvailable(editor, isUndo)) return null;
    return isUndo ? new Undo(myState, editor) : new Redo(myState, editor);
  }

  UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  boolean hasActions() {
    return !myCurrentActions.isEmpty();
  }

  boolean isPhysical() {
    if (myAllAffectedDocuments.isEmpty()) return false;
    for (DocumentReference each : myAllAffectedDocuments) {
      if (isVirtualDocumentChange(each.getFile())) return false;
    }
    return true;
  }

  boolean isUndoAvailable(@NotNull Collection<? extends DocumentReference> refs) {
    if (hasNonUndoableActions()) {
      return false;
    }
    if (refs.isEmpty()) return isGlobal() && hasActions();

    for (DocumentReference each : refs) {
      if (hasChangesOf(each)) return true;
    }
    return false;
  }

  private boolean hasNonUndoableActions() {
    for (UndoableAction each : myCurrentActions) {
      if (each instanceof NonUndoableAction) return true;
    }
    return false;
  }

  private boolean hasChangesOf(DocumentReference ref) {
    return hasChangesOf(ref, false);
  }

  boolean hasChangesOf(DocumentReference ref, boolean onlyDirectChanges) {
    for (UndoableAction action : myCurrentActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) {
        if (!onlyDirectChanges) return true;
      }
      else if (ArrayUtil.contains(ref, refs)) return true;
    }
    return hasActions() && myAdditionalAffectedDocuments.contains(ref);
  }

  void setBeforeState(EditorAndState state) {
    if (myStateBefore == null || !hasActions()) {
      myStateBefore = state;
    }
  }

  void setAfterState(EditorAndState state) {
    myStateAfter = state;
  }

  void addAdditionalAffectedDocuments(@NotNull Collection<? extends DocumentReference> refs) {
    myAllAffectedDocuments.addAll(refs);
    myAdditionalAffectedDocuments.addAll(refs);
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (myAllAffectedDocuments.contains(ref)) {
      myValid = false;
    }
  }
}
