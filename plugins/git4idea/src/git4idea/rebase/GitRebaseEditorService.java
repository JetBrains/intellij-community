/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.rebase;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.components.ServiceManager;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.util.ScriptGenerator;
import org.jetbrains.ide.BuiltInServerManager;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

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
  private final Map<Integer, GitRebaseEditorHandler> myHandlers = new THashMap<>();
  /**
   * The lock for the handlers
   */
  private final Object myHandlersLock = new Object();
  /**
   * Random number generator
   */
  private static final Random oursRandom = new SecureRandom();
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
        generator.addInternal(Integer.toString(BuiltInServerManager.getInstance().getPort()));
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
  public int registerHandler(GitRebaseEditorHandler handler) {
    addInternalHandler();
    Integer rc = null;
    synchronized (myHandlersLock) {
      for (int i = Integer.MAX_VALUE; i > 0; i--) {
        int code = Math.abs(oursRandom.nextInt());
        // note that code might still be negative at this point if it is Integer.MIN_VALUE.
        if (code > 0 && !myHandlers.containsKey(code)) {
          rc = code;
          break;
        }
      }
      if (rc == null) {
        throw new IllegalStateException("There is a problem with random number allocation");
      }
      myHandlers.put(rc, handler);
    }
    return rc;
  }


  /**
   * Unregister handler
   *
   * @param handlerNo the handler number.
   */
  public void unregisterHandler(final int handlerNo) {
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
  GitRebaseEditorHandler getHandler(final int handlerNo) {
    synchronized (myHandlersLock) {
      GitRebaseEditorHandler h = myHandlers.get(handlerNo);
      if (h == null) {
        throw new IllegalStateException("The handler " + handlerNo + " is not registered");
      }
      return h;
    }
  }

  /**
   * Configure handler with editor
   *
   * @param h        the handler to configure
   * @param editorNo the editor number
   */
  public void configureHandler(GitLineHandler h, int editorNo) {
    h.setEnvironment(GitCommand.GIT_EDITOR_ENV, getEditorCommand());
    h.setEnvironment(GitRebaseEditorMain.IDEA_REBASE_HANDER_NO, Integer.toString(editorNo));
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
    public int editCommits(int handlerNo, String path) {
      GitRebaseEditorHandler editor = getHandler(handlerNo);
      return editor.editCommits(path);
    }
  }
}
