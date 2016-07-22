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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CommandMerger {
  private final UndoManagerImpl myManager;
  private Object myLastGroupId;
  private boolean myForcedGlobal;
  private boolean myTransparent;
  private String myCommandName;
  private boolean myValid = true;
  private List<UndoableAction> myCurrentActions = new ArrayList<>();
  private Set<DocumentReference> myAllAffectedDocuments = new THashSet<>();
  private Set<DocumentReference> myAdditionalAffectedDocuments = new THashSet<>();
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private UndoConfirmationPolicy myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

  public CommandMerger(@NotNull UndoManagerImpl manager) {
    myManager = manager;
  }

  public CommandMerger(@NotNull UndoManagerImpl manager, boolean isTransparent) {
    myManager = manager;
    myTransparent = isTransparent;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public void addAction(@NotNull UndoableAction action) {
    myCurrentActions.add(action);
    DocumentReference[] refs = action.getAffectedDocuments();
    if (refs != null) {
      Collections.addAll(myAllAffectedDocuments, refs);
    }
    myForcedGlobal |= action.isGlobal();
  }

  public void commandFinished(String commandName, Object groupId, @NotNull CommandMerger nextCommandToMerge) {
    if (!shouldMerge(groupId, nextCommandToMerge)) {
      flushCurrentCommand();
      myManager.compact();
    }
    merge(nextCommandToMerge);

    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    if (nextCommandToMerge.isTransparent() || !hasActions()) return;

    clearRedoStacks(nextCommandToMerge);

    myLastGroupId = groupId;
    if (myCommandName == null) myCommandName = commandName;
  }

  private boolean shouldMerge(Object groupId, @NotNull CommandMerger nextCommandToMerge) {
    if (isTransparent() || nextCommandToMerge.isTransparent()) {
      return !hasActions() || !nextCommandToMerge.hasActions() || myAllAffectedDocuments.equals(nextCommandToMerge.myAllAffectedDocuments);
    }
    return !myForcedGlobal && !nextCommandToMerge.myForcedGlobal && canMergeGroup(groupId, myLastGroupId);
  }

  public static boolean canMergeGroup(Object groupId, Object lastGroupId) {
    return groupId != null && Comparing.equal(lastGroupId, groupId);
  }

  private void merge(@NotNull CommandMerger nextCommandToMerge) {
    setBeforeState(nextCommandToMerge.myStateBefore);
    myStateAfter = nextCommandToMerge.myStateAfter;
    if (myTransparent) { // todo write test
      if (nextCommandToMerge.hasActions()) {
        myTransparent &= nextCommandToMerge.myTransparent;
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

  void flushCurrentCommand() {
    if (hasActions()) {
      if (!myAdditionalAffectedDocuments.isEmpty()) {
        DocumentReference[] refs = myAdditionalAffectedDocuments.toArray(new DocumentReference[myAdditionalAffectedDocuments.size()]);
        myCurrentActions.add(new BasicUndoableAction(refs) {
          @Override
          public void undo() {
          }

          @Override
          public void redo() {
          }
        });
      }

      myManager.getUndoStacksHolder().addToStacks(new UndoableGroup(myCommandName,
                                                                    isGlobal(),
                                                                    myManager,
                                                                    myStateBefore,
                                                                    myStateAfter,
                                                                    myCurrentActions,
                                                                    myUndoConfirmationPolicy,
                                                                    isTransparent(),
                                                                    myValid));
    }

    reset();
  }

  private void reset() {
    myCurrentActions = new ArrayList<>();
    myAllAffectedDocuments = new THashSet<>();
    myAdditionalAffectedDocuments = new THashSet<>();
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
    myManager.getRedoStacksHolder().clearStacks(isGlobal(), nextMerger.myAllAffectedDocuments);
  }

  boolean isGlobal() {
    return myForcedGlobal || affectsMultiplePhysicalDocs();
  }

  void markAsGlobal() {
    myForcedGlobal = true;
  }

  public boolean isTransparent() {
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
      if (!undoRedo.isTransparent()) break;
      if (!undoRedo.execute(true, false)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    boolean isInsideStartFinishGroup = false;
    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (!undoRedo.execute(false, isInsideStartFinishGroup)) return;
      isInsideStartFinishGroup = undoRedo.myUndoableGroup.isInsideStartFinishGroup(isUndo, isInsideStartFinishGroup);
      if (isInsideStartFinishGroup) continue;
      boolean shouldRepeat = undoRedo.isTransparent() && undoRedo.hasMoreActions();
      if (!shouldRepeat) break;
    }
  }

  @Nullable
  private UndoRedo createUndoOrRedo(FileEditor editor, boolean isUndo) {
    if (!myManager.isUndoOrRedoAvailable(editor, isUndo)) return null;
    return isUndo ? new Undo(myManager, editor) : new Redo(myManager, editor);
  }

  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  boolean hasActions() {
    return !myCurrentActions.isEmpty();
  }

  public boolean isPhysical() {
    if (myAllAffectedDocuments.isEmpty()) return false;
    for (DocumentReference each : myAllAffectedDocuments) {
      if (isVirtualDocumentChange(each.getFile())) return false;
    }
    return true;
  }

  public boolean isUndoAvailable(@NotNull Collection<DocumentReference> refs) {
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

  void addAdditionalAffectedDocuments(@NotNull Collection<DocumentReference> refs) {
    myAllAffectedDocuments.addAll(refs);
    myAdditionalAffectedDocuments.addAll(refs);
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (myAllAffectedDocuments.contains(ref)) {
      myValid = false;
    }
  }
}
