// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EditorActionManagerImpl extends EditorActionManager {
  private static final Key<ReadonlyFragmentModificationHandler> HANDLER_KEY = Key.create("READONLY_FRAGMENT_MODIFICATION_HANDLER");

  private ReadonlyFragmentModificationHandler myReadonlyFragmentsHandler = new DefaultReadOnlyFragmentModificationHandler();

  @Override
  public EditorActionHandler getActionHandler(@NotNull String actionId) {
    return ((EditorAction) ActionManager.getInstance().getAction(actionId)).getHandler();
  }

  @Override
  public EditorActionHandler setActionHandler(@NotNull String actionId, @NotNull EditorActionHandler handler) {
    EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
    return action.setupHandler(handler);
  }

  @Override
  public @NotNull TypedAction getTypedAction() {
    return TypedAction.getInstance();
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentsHandler;
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler(@NotNull Document document) {
    Document doc = PsiDocumentManagerBase.getTopLevelDocument(document);
    ReadonlyFragmentModificationHandler docHandler = doc instanceof DocumentImpl ? doc.getUserData(HANDLER_KEY) : null;
    return docHandler == null ? myReadonlyFragmentsHandler : docHandler;
  }

  @Override
  public void setReadonlyFragmentModificationHandler(@NotNull Document document, @Nullable ReadonlyFragmentModificationHandler handler) {
    Document doc = PsiDocumentManagerBase.getTopLevelDocument(document);
    if (doc instanceof DocumentImpl) {
      doc.putUserData(HANDLER_KEY, handler);
    }
  }

  @Override
  public ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@NotNull ReadonlyFragmentModificationHandler handler) {
    ReadonlyFragmentModificationHandler oldHandler = myReadonlyFragmentsHandler;
    myReadonlyFragmentsHandler = handler;
    return oldHandler;
  }


  private static final class DefaultReadOnlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      Messages.showErrorDialog(EditorBundle.message("guarded.block.modification.attempt.error.message"),
                               EditorBundle.message("guarded.block.modification.attempt.error.title"));
    }
  }
}

