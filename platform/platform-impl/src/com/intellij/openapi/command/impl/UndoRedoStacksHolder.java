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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

class UndoRedoStacksHolder {
  private final Key<LinkedList<UndoableGroup>> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  private final boolean myUndo;

  private final LinkedList<UndoableGroup> myGlobalStack = new LinkedList<>();
  // strongly reference local files for which we can undo file removal
  // document without files and nonlocal files are stored without strong reference
  private final THashMap<DocumentReference, LinkedList<UndoableGroup>> myDocumentStacks = new THashMap<>();
  private final Collection<Document> myDocumentsWithStacks = new WeakList<>();
  private final Collection<VirtualFile> myNonlocalVirtualFilesWithStacks = new WeakList<>();

  public UndoRedoStacksHolder(boolean isUndo) {
    myUndo = isUndo;
  }

  @NotNull
  LinkedList<UndoableGroup> getStack(@NotNull DocumentReference r) {
    return r.getFile() != null ? doGetStackForFile(r) : doGetStackForDocument(r);
  }

  @NotNull
  private LinkedList<UndoableGroup> doGetStackForFile(@NotNull DocumentReference r) {
    LinkedList<UndoableGroup> result;
    VirtualFile file = r.getFile();

    if (!file.isInLocalFileSystem()) {
      result = addWeaklyTrackedEmptyStack(file, myNonlocalVirtualFilesWithStacks);
    }
    else {
      result = myDocumentStacks.get(r);
      if (result == null) {
        result = new LinkedList<>();
        myDocumentStacks.put(r, result);
      }
    }

    return result;
  }

  @NotNull
  private LinkedList<UndoableGroup> doGetStackForDocument(@NotNull DocumentReference r) {
    // If document is not associated with file, we have to store its stack in document
    // itself to avoid memory leaks caused by holding stacks of all documents, ever created, here.
    // And to know, what documents do exist now, we have to maintain weak reference list of them.

    return addWeaklyTrackedEmptyStack(r.getDocument(), myDocumentsWithStacks);
  }

  @NotNull
  private <T extends UserDataHolder> LinkedList<UndoableGroup> addWeaklyTrackedEmptyStack(@NotNull T holder, @NotNull Collection<T> allHolders) {
    LinkedList<UndoableGroup> result = holder.getUserData(STACK_IN_DOCUMENT_KEY);
    if (result == null) {
      holder.putUserData(STACK_IN_DOCUMENT_KEY, result = new LinkedList<>());
      allHolders.add(holder);
    }
    return result;
  }

  boolean canBeUndoneOrRedone(@NotNull Collection<DocumentReference> refs) {
    if (refs.isEmpty()) return !myGlobalStack.isEmpty() && myGlobalStack.getLast().isValid();
    for (DocumentReference each : refs) {
      if (!getStack(each).isEmpty() && getStack(each).getLast().isValid()) return true;
    }
    return false;
  }

  @NotNull
  UndoableGroup getLastAction(@NotNull Collection<DocumentReference> refs) {
    if (refs.isEmpty()) return myGlobalStack.getLast();

    UndoableGroup mostRecentAction = null;
    int mostRecentDocTimestamp = myUndo ? -1 : Integer.MAX_VALUE;

    for (DocumentReference each : refs) {
      LinkedList<UndoableGroup> stack = getStack(each);
      // the stack for a document can be empty in case of compound editors with several documents
      if (stack.isEmpty()) continue;
      UndoableGroup lastAction = stack.getLast();

      int timestamp = lastAction.getCommandTimestamp();
      if (myUndo ? timestamp > mostRecentDocTimestamp : timestamp < mostRecentDocTimestamp) {
        mostRecentAction = lastAction;
        mostRecentDocTimestamp = timestamp;
      }
    }

    // result must not be null
    return mostRecentAction;
  }

  @NotNull
  Set<DocumentReference> collectClashingActions(@NotNull UndoableGroup group) {
    Set<DocumentReference> result = new THashSet<>();

    for (DocumentReference each : group.getAffectedDocuments()) {
      UndoableGroup last = getStack(each).getLast();
      if (last != group) {
        result.addAll(last.getAffectedDocuments());
      }
    }

    if (group.isGlobal()) {
      UndoableGroup last = myGlobalStack.getLast();
      if (last != group) {
        result.addAll(last.getAffectedDocuments());
      }
    }

    return result;
  }

  void addToStacks(@NotNull UndoableGroup group) {
    for (LinkedList<UndoableGroup> each : getAffectedStacks(group)) {
      doAddToStack(each, group, each == myGlobalStack ? UndoManagerImpl.getGlobalUndoLimit() : UndoManagerImpl.getDocumentUndoLimit());
    }
  }

  private void doAddToStack(@NotNull LinkedList<UndoableGroup> stack, @NotNull UndoableGroup group, int limit) {
    if (!group.isUndoable() && stack.isEmpty()) return;

    stack.add(group);
    while (stack.size() > limit) {
      clearStacksFrom(stack.getFirst());
    }
  }

  void removeFromStacks(@NotNull UndoableGroup group) {
    for (LinkedList<UndoableGroup> each : getAffectedStacks(group)) {
      assert each.getLast() == group;
      each.removeLast();
    }
  }

  void clearStacks(boolean clearGlobal, @NotNull Set<DocumentReference> refs) {
    for (LinkedList<UndoableGroup> each : getAffectedStacks(clearGlobal, refs)) {
      while(!each.isEmpty()) {
        clearStacksFrom(each.getLast());
      }
    }

    myDocumentStacks.entrySet().removeIf(each -> each.getValue().isEmpty());
    myDocumentStacks.compact(); // make sure the following entrySet iteration will not go over empty buckets.

    cleanWeaklyTrackedEmptyStacks(myDocumentsWithStacks);
    cleanWeaklyTrackedEmptyStacks(myNonlocalVirtualFilesWithStacks);
  }

  private <T extends UserDataHolder> void cleanWeaklyTrackedEmptyStacks(@NotNull Collection<T> stackHolders) {
    Set<T> holdersToDrop = new THashSet<>();
    for (T holder : stackHolders) {
      List<UndoableGroup> stack = holder.getUserData(STACK_IN_DOCUMENT_KEY);
      if (stack != null && stack.isEmpty()) {
        holder.putUserData(STACK_IN_DOCUMENT_KEY, null);
        holdersToDrop.add(holder);
      }
    }
    stackHolders.removeAll(holdersToDrop);
  }

  private void clearStacksFrom(@NotNull UndoableGroup from) {
    for (LinkedList<UndoableGroup> each : getAffectedStacks(from)) {
      int pos = each.indexOf(from);
      if (pos == -1) continue;

      if (pos > 0) {
        int top = each.size() - pos;
        clearStacksFrom(each.get(pos - 1));
        assert each.size() == top && each.indexOf(from) == 0;
      }
      each.removeFirst();
    }
  }

  @NotNull
  private List<LinkedList<UndoableGroup>> getAffectedStacks(@NotNull UndoableGroup group) {
    return getAffectedStacks(group.isGlobal(), group.getAffectedDocuments());
  }

  @NotNull
  private List<LinkedList<UndoableGroup>> getAffectedStacks(boolean global, @NotNull Collection<DocumentReference> refs) {
    List<LinkedList<UndoableGroup>> result = new ArrayList<>(refs.size() + 1);
    if (global) result.add(myGlobalStack);
    for (DocumentReference each : refs) {
      result.add(getStack(each));
    }
    return result;
  }

  @TestOnly
  void clearAllStacksInTests() {
    clearStacks(true, getAffectedDocuments());
    myGlobalStack.clear();
    myDocumentStacks.clear();
    myDocumentsWithStacks.clear();
    myNonlocalVirtualFilesWithStacks.clear();
  }

  void collectAllAffectedDocuments(@NotNull Collection<DocumentReference> result) {
    for (UndoableGroup each : myGlobalStack) {
      result.addAll(each.getAffectedDocuments());
    }
    collectLocalAffectedDocuments(result);
  }

  private void collectLocalAffectedDocuments(@NotNull Collection<DocumentReference> result) {
    result.addAll(myDocumentStacks.keySet());
    DocumentReferenceManager documentReferenceManager = DocumentReferenceManager.getInstance();

    for (Document each : myDocumentsWithStacks) {
      result.add(documentReferenceManager.create(each));
    }
    for (VirtualFile each : myNonlocalVirtualFilesWithStacks) {
      result.add(documentReferenceManager.create(each));
    }
  }

  @NotNull
  private Set<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new THashSet<>();
    collectAllAffectedDocuments(result);
    return result;
  }

  int getLastCommandTimestamp(@NotNull DocumentReference r) {
    LinkedList<UndoableGroup> stack = getStack(r);
    if (stack.isEmpty()) return 0;
    return Math.max(stack.getFirst().getCommandTimestamp(), stack.getLast().getCommandTimestamp());
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    for (List<UndoableGroup> eachStack : getAffectedStacks(true, Collections.singleton(ref))) {
      for (UndoableGroup eachGroup : eachStack) {
        eachGroup.invalidateActionsFor(ref);
      }
    }
  }
}
