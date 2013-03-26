/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.git4idea.ssh;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.util.io.FileUtilRt;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.util.ScriptGenerator;
import org.jetbrains.ide.WebServerManager;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * <p>The provider of external application scripts called by Git when a remote operation needs communication with the user.</p>
 * <p>
 *   Usage:
 *   <ol>
 *     <li>Get the script from {@link #getScriptPath()}.</li>
 *     <li>Set up proper environment variable
 *         (e.g. {@code GIT_SSH} for SSH connections, or {@code GIT_ASKPASS} for HTTP) pointing to the script.</li>
 *     <li>{@link #registerHandler(Object) Register} the handler of Git requests.</li>
 *     <li>Call Git operation.</li>
 *     <li>If the operation requires user interaction, the registered handler is called via XML RPC protocol.
 *         It can show a dialog in the GUI and return the answer via XML RPC to the external application, that further provides
 *         this value to the Git process.</li>
 *     <li>{@link #unregisterHandler(int) Unregister} the handler after operation has completed.</li>
 *   </ol>
 * </p>
 */
public abstract class GitXmlRpcHandlerService<T> {

  private static final Random RANDOM = new Random();

  @Nullable private File myScriptPath;
  @NotNull private final THashMap<Integer, T> handlers = new THashMap<Integer, T>();

  /**
   * @return the port number for XML RCP
   */
  public int getXmlRcpPort() {
    return WebServerManager.getInstance().waitForStart().getPort();
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public synchronized File getScriptPath() throws IOException {
    if (myScriptPath == null || !myScriptPath.exists()) {
      ScriptGenerator generator = new ScriptGenerator(getScriptTempFilePrefix(), getScriptMainClass());
      generator.addClasses(XmlRpcClientLite.class, DecoderException.class, FileUtilRt.class);
      customizeScriptGenerator(generator);
      myScriptPath = generator.generate();
    }
    return myScriptPath;
  }

  @NotNull
  protected abstract String getScriptTempFilePrefix();

  @NotNull
  protected abstract Class<?> getScriptMainClass();

  /**
   * Adds more classes or resources to the script if needed.
   */
  protected abstract void customizeScriptGenerator(@NotNull ScriptGenerator generator);

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHandler(int)}.
   *
   * @param handler a handler to register
   * @return an identifier to pass to the environment variable
   */
  public synchronized int registerHandler(@NotNull T handler) {
    XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
    if (!xmlRpcServer.hasHandler(getRpcHandlerName())) {
      xmlRpcServer.addHandler(getRpcHandlerName(), createRpcRequestHandlerDelegate());
    }

    while (true) {
      int candidate = RANDOM.nextInt();
      if (candidate == Integer.MIN_VALUE) {
        continue;
      }
      candidate = Math.abs(candidate);
      if (handlers.containsKey(candidate)) {
        continue;
      }
      handlers.put(candidate, handler);
      return candidate;
    }
  }

  /**
   * Returns the name of the handler to be used by XML RPC client to call remote methods of a proper object.
   */
  @NotNull
  protected abstract String getRpcHandlerName();

  /**
   * Creates an implementation of the xml rpc handler, which methods will be called from the external application.
   * This method should just delegate the call to the specific handler of type {@link T}, which can be achieved by {@link #getHandler(int)}.
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
  protected synchronized T getHandler(int key) {
    T rc = handlers.get(key);
    if (rc == null) {
      throw new IllegalStateException("No handler for the key " + key);
    }
    return rc;
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  public synchronized void unregisterHandler(int key) {
    if (handlers.remove(key) == null) {
      throw new IllegalArgumentException("The handler " + key + " is not registered");
    }
  }

}
