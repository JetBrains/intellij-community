package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


class UndoRedoStacksHolder {
  private final Key<LinkedList<UndoableGroup>> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  private final UndoManagerImpl myManager;

  private final LinkedList<UndoableGroup> myGlobalStack = new LinkedList<UndoableGroup>();

  private final Map<DocumentReference, LinkedList<UndoableGroup>> myDocumentStacks = new HashMap<DocumentReference, LinkedList<UndoableGroup>>();
  private final WeakList<Document> myDocumentsWithStacks = new WeakList<Document>();


  public UndoRedoStacksHolder(UndoManagerImpl m) {
    myManager = m;
  }

  public LinkedList<UndoableGroup> getStack(Document d) {
    return getStack(createReferenceOrGetOriginal(d));
  }

  private DocumentReference createReferenceOrGetOriginal(Document d) {
    Document original = myManager.getOriginal(d);
    return DocumentReferenceByDocument.createDocumentReference(original);
  }

  public LinkedList<UndoableGroup> getStack(@NotNull DocumentReference r) {
    return r.getFile() != null ? getStackForFile(r) : getStackForDocument(r);
  }

  @Nullable
  public UndoableGroup getLastAction(@NotNull DocumentReference r) {

    LinkedList<UndoableGroup> documentStack = getStack(r);

    for (int i = myGlobalStack.size() - 1; i >= 0; i--) {
      UndoableGroup group = myGlobalStack.get(i);
      if (isSpecial(group)) {
        if (documentStack.isEmpty() || documentStack.getLast().getCommandCounter() < group.getCommandCounter()) {
          return group;
        } else {
          return documentStack.getLast();
        }
      }
    }
    return documentStack.isEmpty() ? null : documentStack.getLast();
  }

  public boolean hasUndoableActions(@NotNull DocumentReference r) {
    if (!getStack(r).isEmpty()) return true;
    for (UndoableGroup group : myGlobalStack) {
      if (isSpecial(group)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSpecial(UndoableGroup group) {
    return group.isComplex() && group.getAffectedDocuments().isEmpty();
  }

  private LinkedList<UndoableGroup> getStackForFile(DocumentReference r) {
    LinkedList<UndoableGroup> result = myDocumentStacks.get(r);
    if (result == null) {
      result = new LinkedList<UndoableGroup>();
      myDocumentStacks.put(r, result);
    }
    return result;
  }

  private LinkedList<UndoableGroup> getStackForDocument(DocumentReference r) {
    // If document is not associated with file, we have to store its stack in document
    // itself to avoid memory leaks caused by holding stacks of all documents, ever created, here.
    // And to know, what documents do exist now, we have to maintain soft reference list of them.

    Document d = r.getDocument();
    LinkedList<UndoableGroup> result = d.getUserData(STACK_IN_DOCUMENT_KEY);
    if (result == null) {
      result = new LinkedList<UndoableGroup>();
      d.putUserData(STACK_IN_DOCUMENT_KEY, result);
      myDocumentsWithStacks.add(d);
    }
    return result;
  }

  public LinkedList<UndoableGroup> getGlobalStack() {
    return myGlobalStack;
  }


  public void clearFileStack(DocumentReference r) {
    List<UndoableGroup> stack = getStack(r);
    stack.clear();

    if (r.getFile() != null) {
      myDocumentStacks.remove(r);
    }
    else {
      Document d = r.getDocument();
      d.putUserData(STACK_IN_DOCUMENT_KEY, null);
      myDocumentsWithStacks.remove(d);
    }
  }

  public void clearEditorStack(FileEditor e) {
    for (DocumentReference d : myManager.getDocumentReferences(e)) {
      clearFileStack(d);
    }
  }

  public void clearGlobalStack() {
    myGlobalStack.clear();
  }

  public void invalidateAllComplexCommands() {
    invalidateAllComplexCommands(getGlobalStack());
    for (DocumentReference r : getAffectedDocuments()) {
      invalidateAllComplexCommands(getStack(r));
    }
  }

  private void invalidateAllComplexCommands(LinkedList<UndoableGroup> stack) {
    for (UndoableGroup g : stack) {
      g.invalidateIfComplex();
    }
  }

  public void dropHistory() {
    clearGlobalStack();
    for (DocumentReference r : getAffectedDocuments()) {
      clearFileStack(r);
    }
  }

  public void addToLocalStack(DocumentReference r, UndoableGroup g) {
    addToStack(getStack(r), g, UndoManagerImpl.LOCAL_UNDO_LIMIT);
  }

  public void addToGlobalStack(UndoableGroup g) {
    addToStack(getGlobalStack(), g, UndoManagerImpl.GLOBAL_UNDO_LIMIT);
  }

  private void addToStack(LinkedList<UndoableGroup> stack, UndoableGroup g, int limit) {
    stack.addLast(g);
    while (stack.size() > limit) {
      stack.removeFirst();
    }
  }

  public Set<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<DocumentReference>(myDocumentStacks.keySet());
    for (Document d : myDocumentsWithStacks) {
      result.add(DocumentReferenceByDocument.createDocumentReference(d));
    }
    return result;
  }

  public Set<DocumentReference> getGlobalStackAffectedDocuments() {
    HashSet<DocumentReference> result = new HashSet<DocumentReference>();
    for (UndoableGroup g : myGlobalStack) {
      result.addAll(g.getAffectedDocuments());
    }
    return result;
  }

  public int getYoungestCommandAge(DocumentReference r) {
    LinkedList<UndoableGroup> stack = getStack(r);
    if (stack.isEmpty()) return 0;
    return Math.max(stack.getFirst().getCommandCounter(), stack.getLast().getCommandCounter());
  }
}
