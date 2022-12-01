// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.execution.CommandLineUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitScriptGenerator;
import git4idea.config.GitExecutable;
import git4idea.editor.GitRebaseEditorAppHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static git4idea.commands.GitCommand.GIT_EDITOR_ENV;

public final class GitHandlerRebaseEditorManager implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(GitHandlerRebaseEditorManager.class);

  @NotNull private final GitHandler myHandler;
  @NotNull private final GitRebaseEditorHandler myEditorHandler;
  @NotNull private final GitRebaseEditorService myService;

  private final Disposable myDisposable = Disposer.newDisposable();

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
    try {
      GitExecutable executable = myHandler.getExecutable();
      UUID handlerId = myService.registerHandler(myEditorHandler, executable, myDisposable);

      int port = myService.getIdePort();
      File scriptFile = myService.getCallbackScriptPath(executable.getId(), new GitScriptGenerator(executable), false);

      String scriptPath = myHandler.getExecutable().convertFilePath(scriptFile);
      myHandler.addCustomEnvironmentVariable(GIT_EDITOR_ENV, CommandLineUtil.posixQuote(scriptPath));
      myHandler.addCustomEnvironmentVariable(GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV, handlerId.toString());
      myHandler.addCustomEnvironmentVariable(GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV, Integer.toString(port));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void close() {
    Disposer.dispose(myDisposable);
  }
}
