// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class EditorActionManagerImpl extends EditorActionManager {
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
  @NotNull
  public TypedAction getTypedAction() {
    return TypedAction.getInstance();
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentsHandler;
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler(@NotNull final Document document) {
    final Document doc = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    final ReadonlyFragmentModificationHandler docHandler =
      doc instanceof DocumentImpl ? ((DocumentImpl)doc).getReadonlyFragmentModificationHandler() : null;
    return docHandler == null ? myReadonlyFragmentsHandler : docHandler;
  }

  @Override
  public void setReadonlyFragmentModificationHandler(@NotNull final Document document, final ReadonlyFragmentModificationHandler handler) {
    final Document doc = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    if (doc instanceof DocumentImpl) {
      ((DocumentImpl)document).setReadonlyFragmentModificationHandler(handler);
    }
  }

  @Override
  public ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@NotNull ReadonlyFragmentModificationHandler handler) {
    ReadonlyFragmentModificationHandler oldHandler = myReadonlyFragmentsHandler;
    myReadonlyFragmentsHandler = handler;
    return oldHandler;
  }


  private static class DefaultReadOnlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      Messages.showErrorDialog(EditorBundle.message("guarded.block.modification.attempt.error.message"),
                               EditorBundle.message("guarded.block.modification.attempt.error.title"));
    }
  }
}

