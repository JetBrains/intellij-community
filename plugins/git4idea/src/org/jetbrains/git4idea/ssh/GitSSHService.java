/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.trilead.ssh2.KnownHosts;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.util.ScriptGenerator;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

/**
 * The provider of SSH scripts for the Git
 */
public abstract class GitSSHService {

  /**
   * random number generator to use
   */
  private static final Random RANDOM = new Random();
  /**
   * If true, the component has been initialized
   */
  private boolean myInitialized = false;
  /**
   * Path to the generated script
   */
  private File myScriptPath;
  /**
   * Registered handlers
   */
  private final THashMap<Integer, Handler> handlers = new THashMap<Integer, Handler>();

  /**
   * @return the port number for XML RCP
   */
  public abstract int getXmlRcpPort();

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public synchronized File getScriptPath() throws IOException {
    if (myScriptPath == null || !myScriptPath.exists()) {
      ScriptGenerator generator = new ScriptGenerator(GitSSHHandler.GIT_SSH_PREFIX, SSHMain.class, getTempDir());
      generator.addClasses(XmlRpcClientLite.class, DecoderException.class);
      generator.addClasses(KnownHosts.class, FileUtil.class);
      generator.addResource(SSHMainBundle.class, "/org/jetbrains/git4idea/ssh/SSHMainBundle.properties");
      myScriptPath = generator.generate();
    }
    return myScriptPath;
  }

  /**
   * @return the temporary directory to use or null if the default directory might be used
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  protected File getTempDir() {
    return null;
  }

  /**
   * Initialize component
   */
  public void initComponent() {
    if (!myInitialized) {
      registerInternalHandler(GitSSHHandler.HANDLER_NAME, new InternalRequestHandler());
      myInitialized = true;
    }
  }

  /**
   * Register the internal handler to the service
   *
   * @param handlerName the handler name
   * @param handler     the handler implementation
   */
  protected abstract void registerInternalHandler(String handlerName, GitSSHHandler handler);

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHandler(int)}.
   *
   * @param handler a handler to register
   * @return an identifier to pass to the environment variable
   */
  public synchronized int registerHandler(@NotNull Handler handler) {
    initComponent();
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
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  @NotNull
  private synchronized Handler getHandler(int key) {
    Handler rc = handlers.get(key);
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


  /**
   * Handler interface to use by the client code
   */
  public interface Handler {
    /**
     * Verify key
     *
     * @param hostname               a host name
     * @param port                   a port number
     * @param serverHostKeyAlgorithm an algorithm
     * @param serverHostKey          a key
     * @param isNew                  a isNew key
     * @return true if the key is valid
     */
    boolean verifyServerHostKey(final String hostname,
                                final int port,
                                final String serverHostKeyAlgorithm,
                                final String serverHostKey,
                                final boolean isNew);

    /**
     * Ask passphrase
     *
     * @param username      a user name
     * @param keyPath       a key path
     * @param resetPassword
     * @param lastError     the last error for the handler  @return a passphrase or null if dialog was cancelled.
     */
    String askPassphrase(final String username, final String keyPath, boolean resetPassword, final String lastError);

    /**
     * Reply to challenge in keyboard-interactive scenario
     *
     * @param username    a user name
     * @param name        a name of challenge
     * @param instruction a instructions
     * @param numPrompts  number of prompts
     * @param prompt      prompts
     * @param echo        true if the reply for corresponding prompt should be echoed
     * @param lastError   the last error
     * @return replies to the challenges
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    Vector<String> replyToChallenge(final String username,
                                    final String name,
                                    final String instruction,
                                    final int numPrompts,
                                    final Vector<String> prompt,
                                    final Vector<Boolean> echo,
                                    final String lastError);

    /**
     * Ask password
     *
     * @param username      a user name
     * @param resetPassword true if the previous password supplied to the service was incorrect
     * @param lastError     the previous error  @return a password or null if dialog was cancelled.
     */
    String askPassword(final String username, boolean resetPassword, final String lastError);

  }

  /**
   * Internal handler implementation class, do not use it.
   */
  public class InternalRequestHandler implements GitSSHHandler {
    /**
     * {@inheritDoc}
     */
    public boolean verifyServerHostKey(final int handler,
                                       final String hostname,
                                       final int port,
                                       final String serverHostKeyAlgorithm,
                                       final String serverHostKey,
                                       final boolean isNew) {
      return getHandler(handler).verifyServerHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey, isNew);
    }

    /**
     * {@inheritDoc}
     */
    public String askPassphrase(final int handler,
                                final String username,
                                final String keyPath,
                                final boolean resetPassword,
                                final String lastError) {
      return adjustNull(getHandler(handler).askPassphrase(username, keyPath, resetPassword, lastError));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> replyToChallenge(final int handlerNo,
                                           final String username,
                                           final String name,
                                           final String instruction,
                                           final int numPrompts,
                                           final Vector<String> prompt,
                                           final Vector<Boolean> echo,
                                           final String lastError) {
      return adjustNull(getHandler(handlerNo).replyToChallenge(username, name, instruction, numPrompts, prompt, echo, lastError));
    }

    /**
     * {@inheritDoc}
     */
    public String askPassword(final int handlerNo, final String username, final boolean resetPassword, final String lastError) {
      return adjustNull(getHandler(handlerNo).askPassword(username, resetPassword, lastError));
    }

    /**
     * Adjust null value (by converting to {@link GitSSHHandler#XML_RPC_NULL_STRING})
     *
     * @param s a value to adjust
     * @return a string if non-null or {@link GitSSHHandler#XML_RPC_NULL_STRING} if s == null
     */
    private String adjustNull(final String s) {
      return s == null ? XML_RPC_NULL_STRING : s;
    }

    /**
     * Adjust null value (returns empty array)
     *
     * @param s if null return empty array
     * @return s if not null, empty array otherwise
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    private <T> Vector<T> adjustNull(final Vector<T> s) {
      return s == null ? new Vector<T>() : s;
    }
  }
}
