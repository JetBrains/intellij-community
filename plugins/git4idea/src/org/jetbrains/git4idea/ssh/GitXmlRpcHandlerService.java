// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.ssh;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.GitExternalApp;
import org.jetbrains.git4idea.util.ScriptGenerator;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

/**
 * <p>The provider of external application scripts called by Git when a remote operation needs communication with the user.</p>
 * <p>
 *   Usage:
 *   <ol>
 *     <li>Get the script from {@link #getScriptPath()}.</li>
 *     <li>Set up proper environment variable
 *         (e.g. {@code GIT_SSH} for SSH connections, or {@code GIT_ASKPASS} for HTTP) pointing to the script.</li>
 *     <li>{@link #registerHandler(Object, Disposable) Register} the handler of Git requests.</li>
 *     <li>Call Git operation.</li>
 *     <li>If the operation requires user interaction, the registered handler is called via XML RPC protocol.
 *         It can show a dialog in the GUI and return the answer via XML RPC to the external application, that further provides
 *         this value to the Git process.</li>
 *     <li>{@link #unregisterHandler(UUID) Unregister} the handler after operation has completed.</li>
 *   </ol>
 * </p>
 */
public abstract class GitXmlRpcHandlerService<T> {
  private static final Logger LOG = getInstance(GitXmlRpcHandlerService.class);

  @NotNull private final String myScriptTempFilePrefix;
  @NotNull private final String myHandlerName;
  @NotNull private final Class<? extends GitExternalApp> myScriptMainClass;

  @Nullable private File myBatchScriptPath;
  @Nullable private File myShellScriptPath;
  @NotNull private final Object SCRIPT_FILE_LOCK = new Object();

  @NotNull private final THashMap<UUID, HandlerWrapper> handlers = new THashMap<>();
  @NotNull private final Object HANDLERS_LOCK = new Object();

  /**
   * @param handlerName Returns the name of the handler to be used by XML RPC client to call remote methods of a proper object.
   * @param aClass      Main class of the external application invoked by Git,
   *                    which is able to handle its requests and pass to the main IDEA instance.
   */
  protected GitXmlRpcHandlerService(@NotNull String prefix, @NotNull String handlerName, @NotNull Class<? extends GitExternalApp> aClass) {
    myScriptTempFilePrefix = prefix;
    myHandlerName = handlerName;
    myScriptMainClass = aClass;
  }

  /**
   * @return the port number for XML RCP
   */
  public int getXmlRcpPort() {
    return BuiltInServerManager.getInstance().waitForStart().getPort();
  }

  @NotNull
  public File getScriptPath() throws IOException {
    return getScriptPath(SystemInfo.isWindows);
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public File getScriptPath(boolean useBatchFile) throws IOException {
    ScriptGenerator generator = new ScriptGenerator(myScriptTempFilePrefix, myScriptMainClass);
    generator.addClasses(XmlRpcClientLite.class, DecoderException.class, FileUtilRt.class);
    customizeScriptGenerator(generator);

    synchronized (SCRIPT_FILE_LOCK) {
      if (useBatchFile) {
        if (myBatchScriptPath == null || !myBatchScriptPath.exists()) {
          myBatchScriptPath = generator.generate(useBatchFile);
        }
        return myBatchScriptPath;
      }
      else {
        if (myShellScriptPath == null || !myShellScriptPath.exists()) {
          myShellScriptPath = generator.generate(useBatchFile);
        }
        return myShellScriptPath;
      }
    }
  }

  /**
   * Adds more classes or resources to the script if needed.
   */
  protected abstract void customizeScriptGenerator(@NotNull ScriptGenerator generator);

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHandler(UUID)}.
   *
   * @param handler          a handler to register
   * @param parentDisposable a disposable to unregister the handler if it doesn't get unregistered manually
   * @return an identifier to pass to the environment variable
   */
  @NotNull
  public UUID registerHandler(@NotNull T handler) {
    synchronized (HANDLERS_LOCK) {
      XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
      if (!xmlRpcServer.hasHandler(myHandlerName)) {
        xmlRpcServer.addHandler(myHandlerName, createRpcRequestHandlerDelegate());
      }

      final UUID key = UUID.randomUUID();
      HandlerWrapper handlerWrapper = new HandlerWrapper(handler, key);
      handlers.put(key, handlerWrapper);
      Disposer.register(parentDisposable, handlerWrapper);
      return key;
    }
  }

  /**
   * Creates an implementation of the xml rpc handler, which methods will be called from the external application.
   * This method should just delegate the call to the specific handler of type {@link T}, which can be achieved by
   * {@link #getHandler(UUID)}.
   *
   * @return New instance of the xml rpc handler delegate.
   */
  @NotNull
  protected abstract Object createRpcRequestHandlerDelegate();

  /**
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  @NotNull
  protected T getHandler(@NotNull UUID key) {
    synchronized (HANDLERS_LOCK) {
      HandlerWrapper handlerWrapper = handlers.get(key);
      if (handlerWrapper == null) {
        throw new IllegalStateException("No handler for the key " + key);
      }
      return handlerWrapper.handler;
    }
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  public void unregisterHandler(@NotNull UUID key) {
    HandlerWrapper handlerWrapper = removeHandlerWrapper(key); // Remove from handlers
    handlerWrapper.onRemoved(); // Let the wrapper know that it has been removed from handlers to prevent second removal attempt.
    Disposer.dispose(handlerWrapper); // Remove from the Disposer's tree.
  }

  @NotNull
  private HandlerWrapper removeHandlerWrapper(@NotNull UUID key) {
    synchronized (HANDLERS_LOCK) {
      HandlerWrapper handlerWrapper = handlers.remove(key);
      if (handlerWrapper == null) {
        throw new IllegalArgumentException("The handler " + key + " is not registered");
      }
      return handlerWrapper;
    }
  }

  private class HandlerWrapper implements Disposable {
    @NotNull private final T handler;
    @NotNull private final UUID key;
    private boolean removed;

    HandlerWrapper(@NotNull T handler, @NotNull UUID key) {
      this.handler = handler;
      this.key = key;
    }

    @Override
    public void dispose() {
      if (!removed) {
        removeHandlerWrapper(key);
      }
    }

    void onRemoved() {
      removed = true;
    }
  }
}
