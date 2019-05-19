// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class GitSSHXmlRpcClient implements GitSSHHandler {
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
  GitSSHXmlRpcClient(final int port, final boolean batchMode) throws IOException {
    myClient = batchMode ? null : new XmlRpcClientLite("127.0.0.1", port);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean verifyServerHostKey(String token,
                                     final String hostname,
                                     final int port,
                                     final String serverHostKeyAlgorithm,
                                     final String serverHostKey,
                                     final boolean isNew) {
    if (myClient == null) {
      return false;
    }
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(hostname);
    parameters.add(port);
    parameters.add(serverHostKeyAlgorithm);
    parameters.add(serverHostKey);
    parameters.add(isNew);
    try {
      return ((Boolean)myClient.execute(methodName("verifyServerHostKey"), parameters)).booleanValue();
    }
    catch (XmlRpcException | IOException e) {
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
    return GitSSHHandler.HANDLER_NAME + "." + method;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  @SuppressWarnings("unchecked")
  public String askPassphrase(String token,
                              final String username,
                              final String keyPath,
                              final boolean resetPassword,
                              final String lastError) {
    if (myClient == null) {
      return null;
    }
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(username);
    parameters.add(keyPath);
    parameters.add(resetPassword);
    parameters.add(lastError);
    try {
      return adjustNull(((String)myClient.execute(methodName("askPassphrase"), parameters)));
    }
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  @SuppressWarnings("unchecked")
  public Vector<String> replyToChallenge(String token,
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
    parameters.add(token);
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
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  @SuppressWarnings("unchecked")
  public String askPassword(String token, final String username, final boolean resetPassword, final String lastError) {
    if (myClient == null) {
      return null;
    }
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(username);
    parameters.add(resetPassword);
    parameters.add(lastError);
    try {
      return adjustNull(((String)myClient.execute(methodName("askPassword"), parameters)));
    }
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public String setLastSuccessful(String token, String userName, String method, String error) {
    if (myClient == null) {
      return "";
    }
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(userName);
    parameters.add(method);
    parameters.add(error);
    try {
      return (String)myClient.execute(methodName("setLastSuccessful"), parameters);
    }
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public String getLastSuccessful(String token, String userName) {
    if (myClient == null) {
      return "";
    }
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(userName);
    try {
      return (String)myClient.execute(methodName("getLastSuccessful"), parameters);
    }
    catch (XmlRpcException | IOException e) {
      log("getLastSuccessful failed. token: " + token + ", userName: " + userName + ", client: " + myClient.getURL());
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  /**
   * Since XML RPC client does not understand null values, the value should be
   * adjusted (The password is {@code "-"} if null, {@code "+"+s) if non-null).
   *
   * @param s a value to adjust
   * @return adjusted value.
   */
  @Nullable
  private static String adjustNull(final String s) {
    return s.charAt(0) == '-' ? null : s.substring(1);
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

  private static void log(String s) {
    System.err.println(s);
  }
}
