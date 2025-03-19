// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public abstract class StacksHolderBase<E, ECollection extends Collection<E>>  {
  protected static final Logger LOG = Logger.getInstance(StacksHolderBase.class);

  private final Key<ECollection> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  // strongly reference local files for which we can undo file removal
  // document without files and nonlocal files are stored without strong reference
  final Map<DocumentReference, ECollection> myDocumentStacks = CollectionFactory.createSmallMemoryFootprintMap();
  final Collection<Document> myDocumentsWithStacks = new WeakList<>();
  final Collection<VirtualFile> myNonlocalVirtualFilesWithStacks = new WeakList<>();

  @NotNull
  ECollection getStack(@NotNull DocumentReference r) {
    return r.getFile() != null ? doGetStackForFile(r) : doGetStackForDocument(r);
  }

  private @NotNull ECollection doGetStackForFile(@NotNull DocumentReference r) {
    ECollection result;
    VirtualFile file = r.getFile();

    if (file.isInLocalFileSystem()) {
      result = myDocumentStacks.computeIfAbsent(r, __ -> newCollection());
    }
    else {
      result = addWeaklyTrackedEmptyStack(file, myNonlocalVirtualFilesWithStacks);
    }

    return result;
  }

  protected abstract ECollection newCollection();

  private @NotNull ECollection doGetStackForDocument(@NotNull DocumentReference r) {
    // If document is not associated with file, we have to store its stack in document
    // itself to avoid memory leaks caused by holding stacks of all documents, ever created, here.
    // And to know, what documents do exist now, we have to maintain weak reference list of them.

    return addWeaklyTrackedEmptyStack(r.getDocument(), myDocumentsWithStacks);
  }

  private @NotNull <T extends UserDataHolder> ECollection addWeaklyTrackedEmptyStack(@NotNull T holder, @NotNull Collection<? super T> allHolders) {
    ECollection result = holder.getUserData(STACK_IN_DOCUMENT_KEY);
    if (result == null) {
      holder.putUserData(STACK_IN_DOCUMENT_KEY, result = newCollection());
      allHolders.add(holder);
    }
    return result;
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
      Collection<E> stack = holder.getUserData(STACK_IN_DOCUMENT_KEY);
      if (stack != null && stack.isEmpty()) {
        holder.putUserData(STACK_IN_DOCUMENT_KEY, null);
        holdersToDrop.add(holder);
      }
    }
    stackHolders.removeAll(holdersToDrop);
  }
}
