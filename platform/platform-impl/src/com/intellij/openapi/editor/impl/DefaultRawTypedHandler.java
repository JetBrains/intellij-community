// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.editorActions.NonWriteAccessTypedHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

public final class DefaultRawTypedHandler implements TypedActionHandlerEx {
  private final TypedAction myAction;
  private CommandToken myCurrentCommandToken;
  private boolean myInOuterCommand;

  DefaultRawTypedHandler(TypedAction action) {
    myAction = action;
  }

  @Override
  public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (editor.isViewer() || !editor.getDocument().isWritable()) return;

    TypedActionHandler handler = myAction.getHandler();

    if (handler instanceof TypedActionHandlerEx) {
      ((TypedActionHandlerEx)handler).beforeExecute(editor, c, context, plan);
    }
  }

  @Override
  public void execute(final @NotNull Editor editor, final char charTyped, final @NotNull DataContext dataContext) {
    CommandProcessorEx commandProcessorEx = (CommandProcessorEx)CommandProcessor.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (myCurrentCommandToken != null) {
      throw new IllegalStateException("Unexpected reentrancy of DefaultRawTypedHandler");
    }
    myCurrentCommandToken = commandProcessorEx.startCommand(project, "", editor.getDocument(), UndoConfirmationPolicy.DEFAULT);
    myInOuterCommand = myCurrentCommandToken == null;
    try {
      FileDocumentManager.WriteAccessStatus writeAccess =
        FileDocumentManager.getInstance().requestWritingStatus(editor.getDocument(), editor.getProject());
      if (!writeAccess.hasWriteAccess()) {
        for (NonWriteAccessTypedHandler handler : NonWriteAccessTypedHandler.EP_NAME.getExtensionList()) {
          if (handler.isApplicable(editor, charTyped, dataContext)) {
            try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
              handler.handle(editor, charTyped, dataContext);
            }
            return;
          }
        }

        HintManager.getInstance().showInformationHint(editor, writeAccess.getReadOnlyMessage(), writeAccess.getHyperlinkListener());
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(editor.getDocument(), editor.getProject()) {
        @Override
        public void run() {
          Document doc = editor.getDocument();
          doc.startGuardedBlockChecking();
          try {
            myAction.getHandler().execute(editor, charTyped, dataContext);
          }
          catch (ReadOnlyFragmentModificationException e) {
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
          }
          finally {
            doc.stopGuardedBlockChecking();
          }
        }
      });
    }
    finally {
      if (!myInOuterCommand) {
        commandProcessorEx.finishCommand(myCurrentCommandToken, null);
        myCurrentCommandToken = null;
      }
      myInOuterCommand = false;
    }
  }

  public void beginUndoablePostProcessing() {
    if (myInOuterCommand) {
      return;
    }
    if (myCurrentCommandToken == null) {
      throw new IllegalStateException("Not in a typed action at this time");
    }
    CommandProcessorEx commandProcessorEx = (CommandProcessorEx)CommandProcessor.getInstance();
    Project project = myCurrentCommandToken.getProject();
    commandProcessorEx.finishCommand(myCurrentCommandToken, null);
    myCurrentCommandToken = commandProcessorEx.startCommand(project, "", null, UndoConfirmationPolicy.DEFAULT);
  }
}
