// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.components.ServiceManager;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.util.ScriptGenerator;
import org.jetbrains.ide.BuiltInServerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The service that generates editor script for
 */
public class GitRebaseEditorService {
  /**
   * The editor command that is set to env variable
   */
  private String myEditorCommand;
  /**
   * The lock object
   */
  private final Object myScriptLock = new Object();
  /**
   * The handlers to use
   */
  private final Map<UUID, GitRebaseEditorHandler> myHandlers = new HashMap<>();
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
    final GitRebaseEditorService service = ServiceManager.getService(GitRebaseEditorService.class);
    if (service == null) {
      throw new IllegalStateException("The service " + GitRebaseEditorService.class.getName() + " cannot be located");
    }
    return service;
  }

  private void addInternalHandler() {
    XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
    if (!xmlRpcServer.hasHandler(GitRebaseEditorMain.HANDLER_NAME)) {
      xmlRpcServer.addHandler(GitRebaseEditorMain.HANDLER_NAME, new InternalHandler());
    }
  }

  /**
   * Get editor command
   *
   * @return the editor command
   */
  @NotNull
  public synchronized String getEditorCommand() {
    synchronized (myScriptLock) {
      if (myEditorCommand == null) {
        ScriptGenerator generator = new ScriptGenerator(GIT_REBASE_EDITOR_PREFIX, GitRebaseEditorMain.class);
        generator.addInternal(Integer.toString(BuiltInServerManager.getInstance().waitForStart().getPort()));
        generator.addClasses(XmlRpcClientLite.class, DecoderException.class);
        myEditorCommand = generator.commandLine();
      }
      return myEditorCommand;
    }
  }

  /**
   * Register the handler in the service
   *
   * @param handler the handler to register
   * @return the handler identifier
   */
  @NotNull
  public UUID registerHandler(@NotNull GitRebaseEditorHandler handler) {
    addInternalHandler();
    synchronized (myHandlersLock) {
      UUID key = UUID.randomUUID();
      myHandlers.put(key, handler);
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
  GitRebaseEditorHandler getHandler(@NotNull UUID handlerNo) {
    synchronized (myHandlersLock) {
      GitRebaseEditorHandler h = myHandlers.get(handlerNo);
      if (h == null) {
        throw new IllegalStateException("The handler " + handlerNo + " is not registered");
      }
      return h;
    }
  }

  /**
   * The internal xml rcp handler
   */
  public class InternalHandler {
    /**
     * Edit commits for the rebase operation
     *
     * @param handlerNo the handler no
     * @param path      the path to edit
     * @return exit code
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public int editCommits(@NotNull String handlerNo, String path) {
      GitRebaseEditorHandler editor = getHandler(UUID.fromString(handlerNo));
      return editor.editCommits(path);
    }
  }
}
