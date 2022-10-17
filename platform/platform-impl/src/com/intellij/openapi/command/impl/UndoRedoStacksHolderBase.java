// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class UndoRedoStacksHolderBase<E> {
  protected static final Logger LOG = Logger.getInstance(UndoRedoStacksHolderBase.class);

  private final Key<LinkedList<E>> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  final boolean myUndo;

  // strongly reference local files for which we can undo file removal
  // document without files and nonlocal files are stored without strong reference
  final Map<DocumentReference, LinkedList<E>> myDocumentStacks = CollectionFactory.createSmallMemoryFootprintMap();
  final Collection<Document> myDocumentsWithStacks = new WeakList<>();
  final Collection<VirtualFile> myNonlocalVirtualFilesWithStacks = new WeakList<>();

  UndoRedoStacksHolderBase(boolean isUndo) {
    myUndo = isUndo;
  }

  @NotNull
  LinkedList<E> getStack(@NotNull DocumentReference r) {
    return r.getFile() != null ? doGetStackForFile(r) : doGetStackForDocument(r);
  }

  @NotNull
  private LinkedList<E> doGetStackForFile(@NotNull DocumentReference r) {
    LinkedList<E> result;
    VirtualFile file = r.getFile();

    if (file.isInLocalFileSystem()) {
      result = myDocumentStacks.computeIfAbsent(r, __ -> new LinkedList<>());
    }
    else {
      result = addWeaklyTrackedEmptyStack(file, myNonlocalVirtualFilesWithStacks);
    }

    return result;
  }

  @NotNull
  private LinkedList<E> doGetStackForDocument(@NotNull DocumentReference r) {
    // If document is not associated with file, we have to store its stack in document
    // itself to avoid memory leaks caused by holding stacks of all documents, ever created, here.
    // And to know, what documents do exist now, we have to maintain weak reference list of them.

    return addWeaklyTrackedEmptyStack(r.getDocument(), myDocumentsWithStacks);
  }

  @NotNull
  private <T extends UserDataHolder> LinkedList<E> addWeaklyTrackedEmptyStack(@NotNull T holder, @NotNull Collection<? super T> allHolders) {
    LinkedList<E> result = holder.getUserData(STACK_IN_DOCUMENT_KEY);
    if (result == null) {
      holder.putUserData(STACK_IN_DOCUMENT_KEY, result = new LinkedList<>());
      allHolders.add(holder);
    }
    return result;
  }

  @NotNull
  String getStacksDescription() {
    return myUndo ? "undo stacks" : "redo stacks";
  }

  void removeEmptyStacks() {
    myDocumentStacks.entrySet().removeIf(each -> each.getValue().isEmpty());
    // make sure the following entrySet iteration will not go over empty buckets.
    CollectionFactory.trimMap(myDocumentStacks);

    cleanWeaklyTrackedEmptyStacks(myDocumentsWithStacks);
    cleanWeaklyTrackedEmptyStacks(myNonlocalVirtualFilesWithStacks);
  }

  // remove all references to document to avoid memory leaks
  void clearDocumentReferences(@NotNull Document document) {
    myDocumentsWithStacks.remove(document);
    // DocumentReference created from file is not equal to ref created from document from that file, so have to check for leaking both
    DocumentReference referenceFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference referenceDoc = new DocumentReferenceByDocument(document);
    myDocumentStacks.remove(referenceFile);
    myDocumentStacks.remove(referenceDoc);
  }

  private <T extends UserDataHolder> void cleanWeaklyTrackedEmptyStacks(@NotNull Collection<T> stackHolders) {
    Set<T> holdersToDrop = new HashSet<>();
    for (T holder : stackHolders) {
      List<E> stack = holder.getUserData(STACK_IN_DOCUMENT_KEY);
      if (stack != null && stack.isEmpty()) {
        holder.putUserData(STACK_IN_DOCUMENT_KEY, null);
        holdersToDrop.add(holder);
      }
    }
    stackHolders.removeAll(holdersToDrop);
  }
}
