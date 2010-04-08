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

import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Vector;

/**
 * Client for IDEA SSH GUI event handler
 */
@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class GitSSHIdeaClient implements GitSSHHandler {
  /**
   * The string used to indicate missing value
   */
  private static final String XML_RPC_NULL_STRING = "\u0000";
  /**
   * Name of the handler
   */
  @NonNls private static final String HANDLER_NAME = "Git4ideaSSHHandler";
  /**
   * XML RPC client
   */
  @Nullable private final XmlRpcClientLite myClient;

  /**
   * A constructor
   *
   * @param port      port number
   * @param batchMode if true, the client is run in the batch mode, so nothing should be prompted
   * @throws IOException if there is IO problem
   */
  GitSSHIdeaClient(final int port, final boolean batchMode) throws IOException {
    //noinspection HardCodedStringLiteral
    myClient = batchMode ? null : new XmlRpcClientLite("localhost", port);
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public boolean verifyServerHostKey(final int handler,
                                     final String hostname,
                                     final int port,
                                     final String serverHostKeyAlgorithm,
                                     final String serverHostKey,
                                     final boolean isNew) {
    if (myClient == null) {
      return false;
    }
    Vector parameters = new Vector();
    parameters.add(handler);
    parameters.add(hostname);
    parameters.add(port);
    parameters.add(serverHostKeyAlgorithm);
    parameters.add(serverHostKey);
    parameters.add(isNew);
    try {
      return ((Boolean)myClient.execute(methodName("verifyServerHostKey"), parameters)).booleanValue();
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * Get the full method name
   *
   * @param method short name of the method
   * @return full method name
   */
  private static String methodName(@NonNls final String method) {
    return HANDLER_NAME + "." + method;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public String askPassphrase(final int handler, final String username, final String keyPath, final boolean resetPassword, final String lastError) {
    if (myClient == null) {
      return null;
    }
    Vector parameters = new Vector();
    parameters.add(handler);
    parameters.add(username);
    parameters.add(keyPath);
    parameters.add(resetPassword);
    parameters.add(lastError);
    try {
      return adjustNull(((String)myClient.execute(methodName("askPassphrase"), parameters)));
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public Vector<String> replyToChallenge(final int handlerNo,
                                         final String username,
                                         final String name,
                                         final String instruction,
                                         final int numPrompts,
                                         final Vector<String> prompt,
                                         final Vector<Boolean> echo,
                                         final String lastError) {
    if (myClient == null) {
      return null;
    }
    Vector parameters = new Vector();
    parameters.add(handlerNo);
    parameters.add(username);
    parameters.add(name);
    parameters.add(instruction);
    parameters.add(numPrompts);
    parameters.add(prompt);
    parameters.add(echo);
    parameters.add(lastError);
    try {
      return adjustNull((Vector<String>)myClient.execute(methodName("replyToChallenge"), parameters));
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public String askPassword(final int handlerNo, final String username, final boolean resetPassword, final String lastError) {
    if (myClient == null) {
      return null;
    }
    Vector parameters = new Vector();
    parameters.add(handlerNo);
    parameters.add(username);
    parameters.add(resetPassword);
    parameters.add(lastError);
    try {
      return adjustNull(((String)myClient.execute(methodName("askPassword"), parameters)));
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * Since XML RPC client does not understand null values, the value should be
   * adjusted. This is done by replacing string {@code "\u0000"} with null.
   *
   * @param s a value to adjust
   * @return adjusted value.
   */
  @Nullable
  private static String adjustNull(final String s) {
    return XML_RPC_NULL_STRING.equals(s) ? null : s;
  }

  /**
   * Since XML RPC client does not understand null values, the value should be
   * adjusted. This is done by replacing empty array with null.
   *
   * @param s a value to adjust
   * @return adjusted value.
   */
  @Nullable
  private static <T> Vector<T> adjustNull(final Vector<T> s) {
    return s.size() == 0 ? null : s;
  }
}
