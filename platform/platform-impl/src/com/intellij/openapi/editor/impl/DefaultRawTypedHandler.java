// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultRawTypedHandler implements TypedActionHandlerEx {
  private final TypedAction typedAction;
  private @Nullable CommandToken currentCommand;
  private boolean isInOuterCommand;

  DefaultRawTypedHandler(TypedAction action) {
    typedAction = action;
  }

  @Override
  public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (editor.isViewer() || !editor.getDocument().isWritable()) {
      return;
    }
    TypedActionHandler handler = typedAction.getHandler();
    if (handler instanceof TypedActionHandlerEx handlerEx) {
      handlerEx.beforeExecute(editor, c, context, plan);
    }
  }

  @Override
  public void execute(final @NotNull Editor editor, final char charTyped, final @NotNull DataContext dataContext) {
    if (currentCommand != null) {
      throw new IllegalStateException("Unexpected reentrancy of DefaultRawTypedHandler");
    }
    CommandProcessorEx commandProcessor = (CommandProcessorEx)CommandProcessor.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Document document = editor.getDocument();
    currentCommand = commandProcessor.startCommand(project, "", /*groupId=*/ document, UndoConfirmationPolicy.DEFAULT);
    isInOuterCommand = currentCommand == null;
    try {
      boolean isWritable = EditorModificationUtil.requestWriting(editor, charTyped, dataContext);
      if (isWritable) {
        EditorThreading.write(() -> {
          document.startGuardedBlockChecking();
          try {
            typedAction.getHandler().execute(editor, charTyped, dataContext);
          } catch (ReadOnlyFragmentModificationException e) {
            var readOnlyHandler = EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document);
            readOnlyHandler.handle(e);
          } finally {
            document.stopGuardedBlockChecking();
          }
        });
      }
    } finally {
      if (!isInOuterCommand) {
        commandProcessor.finishCommand(currentCommand, null);
        currentCommand = null;
      }
      isInOuterCommand = false;
    }
  }

  public void beginUndoablePostProcessing() {
    if (isInOuterCommand) {
      return;
    }
    if (currentCommand == null) {
      throw new IllegalStateException("Not in a typed action at this time");
    }
    Project project = currentCommand.getProject();
    if (isCommandRestartSupported(project)) {
      CommandProcessorEx commandProcessor = (CommandProcessorEx)CommandProcessor.getInstance();
      commandProcessor.finishCommand(currentCommand, null);
      currentCommand = commandProcessor.startCommand(project, "", null, UndoConfirmationPolicy.DEFAULT);
    }
  }

  private static boolean isCommandRestartSupported(@Nullable Project project) {
    UndoManager undoManager = project == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(project);
    return ((UndoManagerImpl)undoManager).getUndoCapabilities().isCommandRestartSupported();
  }
}
