// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.editor.GitRebaseEditorXmlRpcHandler;

import java.util.UUID;

import static git4idea.commands.GitCommand.GIT_EDITOR_ENV;

public final class GitHandlerRebaseEditorManager implements AutoCloseable {
  @NotNull private final GitHandler myHandler;
  @NotNull private final GitRebaseEditorHandler myEditorHandler;
  @NotNull private final GitRebaseEditorService myService;

  @Nullable private UUID myHandlerId;

  /**
   * Configure handler with editor
   */
  @NotNull
  public static GitHandlerRebaseEditorManager prepareEditor(GitHandler h, @NotNull GitRebaseEditorHandler editorHandler) {
    GitHandlerRebaseEditorManager manager = new GitHandlerRebaseEditorManager(h, editorHandler);
    GitUtil.tryRunOrClose(manager, () -> {
      manager.prepareEditor();
    });
    return manager;
  }

  private GitHandlerRebaseEditorManager(@NotNull GitHandler handler, @NotNull GitRebaseEditorHandler editorHandler) {
    myHandler = handler;
    myEditorHandler = editorHandler;
    myService = GitRebaseEditorService.getInstance();
  }

  private void prepareEditor() {
    if (myHandler.containsCustomEnvironmentVariable(GIT_EDITOR_ENV)) return;
    myHandlerId = myService.registerHandler(myHandler, myEditorHandler);
    myHandler.addCustomEnvironmentVariable(GIT_EDITOR_ENV, myService.getEditorCommand(myHandler.getExecutable()));
    myHandler.addCustomEnvironmentVariable(GitRebaseEditorXmlRpcHandler.IJ_EDITOR_HANDLER_ENV, myHandlerId.toString());
  }

  @Override
  public void close() {
    if (myHandlerId != null) {
      myService.unregisterHandler(myHandlerId);
      myHandlerId = null;
    }
  }
}
