// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.externalProcessAuthHelper.ScriptGenerator;
import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Pair;
import git4idea.commands.GitHandler;
import git4idea.config.GitExecutable;
import git4idea.editor.GitRebaseEditorApp;
import git4idea.editor.GitRebaseEditorXmlRpcHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The service that generates editor script for
 */
@Service(Service.Level.APP)
public final class GitRebaseEditorService implements Disposable {
  /**
   * The lock object
   */
  private final Object myScriptLock = new Object();
  /**
   * The handlers to use
   */
  private final Map<UUID, Pair<GitRebaseEditorHandler, GitExecutable>> myHandlers = new HashMap<>();
  /**
   * The lock for the handlers
   */
  private final Object myHandlersLock = new Object();
  /**
   * The prefix for rebase editors
   */
  @NonNls private static final String GIT_REBASE_EDITOR_PREFIX = "git-rebase-editor-";

  /**
   * @return an instance of the server
   */
  @NotNull
  public static GitRebaseEditorService getInstance() {
    final GitRebaseEditorService service = ApplicationManager.getApplication().getService(GitRebaseEditorService.class);
    if (service == null) {
      throw new IllegalStateException("The service " + GitRebaseEditorService.class.getName() + " cannot be located");
    }
    return service;
  }

  private void addInternalHandler() {
    XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
    if (!xmlRpcServer.hasHandler(GitRebaseEditorXmlRpcHandler.HANDLER_NAME)) {
      xmlRpcServer.addHandler(GitRebaseEditorXmlRpcHandler.HANDLER_NAME, new InternalHandlerRebase());
    }
  }

  @Override
  public void dispose() {
    XmlRpcServer xmlRpcServer = ApplicationManager.getApplication().getServiceIfCreated(XmlRpcServer.class);
    if (xmlRpcServer != null) {
      xmlRpcServer.removeHandler(GitRebaseEditorXmlRpcHandler.HANDLER_NAME);
    }
  }

  /**
   * Get editor command
   *
   * @return the editor command
   */
  @NotNull
  public synchronized String getEditorCommand(@NotNull GitExecutable executable) {
    synchronized (myScriptLock) {
      ScriptGenerator generator = new ScriptGenerator(GIT_REBASE_EDITOR_PREFIX, GitRebaseEditorApp.class);
      generator.addInternal(Integer.toString(BuiltInServerManager.getInstance().waitForStart().getPort()));
      return generator.commandLine(executable instanceof ScriptGenerator.CustomScriptCommandLineBuilder
                                   ? (ScriptGenerator.CustomScriptCommandLineBuilder)executable : null);
    }
  }

  /**
   * Register the handler in the service
   *
   * @param handler the handler to register
   * @return the handler identifier
   */
  @NotNull
  public UUID registerHandler(@NotNull GitHandler handler, @NotNull GitRebaseEditorHandler editorHandler) {
    addInternalHandler();
    synchronized (myHandlersLock) {
      UUID key = UUID.randomUUID();
      myHandlers.put(key, Pair.create(editorHandler, handler.getExecutable()));
      return key;
    }
  }

  /**
   * Unregister handler
   *
   * @param handlerNo the handler number.
   */
  public void unregisterHandler(@NotNull UUID handlerNo) {
    synchronized (myHandlersLock) {
      if (myHandlers.remove(handlerNo) == null) {
        throw new IllegalStateException("The handler " + handlerNo + " has been already removed");
      }
    }
  }

  /**
   * Get handler
   *
   * @param handlerNo the handler number.
   */
  @NotNull
  Pair<GitRebaseEditorHandler, GitExecutable> getHandler(@NotNull UUID handlerNo) {
    synchronized (myHandlersLock) {
      Pair<GitRebaseEditorHandler, GitExecutable> pair = myHandlers.get(handlerNo);
      if (pair == null) {
        throw new IllegalStateException("The handler " + handlerNo + " is not registered");
      }
      return pair;
    }
  }

  /**
   * The internal xml rcp handler
   */
  public class InternalHandlerRebase implements GitRebaseEditorXmlRpcHandler {
    @Override
    @SuppressWarnings("UnusedDeclaration")
    public int editCommits(@NotNull String handlerNo, @NotNull String path, @NotNull String workingDir) {
      Pair<GitRebaseEditorHandler, GitExecutable> pair = getHandler(UUID.fromString(handlerNo));
      GitExecutable executable = pair.second;
      GitRebaseEditorHandler editorHandler = pair.first;

      File file = executable.convertFilePathBack(path, new File(workingDir));

      return editorHandler.editCommits(file);
    }
  }
}
