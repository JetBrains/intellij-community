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
package org.jetbrains.git4idea.ssh;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Vector;

/**
 * An interface for GIT SSH handler
 */
public interface GitSSHHandler {
  /**
   * The prefix of the ssh script name
   */
  @NonNls String GIT_SSH_PREFIX = "intellij-git-ssh";
  /**
   * Name of environment variable for SSH handler access token
   */
  @NonNls String SSH_HANDLER_ENV = "GIT4IDEA_SSH_HANDLER";
  @NonNls String SSH_IGNORE_KNOWN_HOSTS_ENV = "GIT4IDEA_SSH_IGNORE_KNOWN_HOSTS";
  @NonNls String SSH_PORT_ENV = "GIT4IDEA_SSH_PORT";
  /**
   * Name of environment variable for SSH executable
   */
  @NonNls String GIT_SSH_ENV = "GIT_SSH";
  /**
   * Name of environment variable for SSH executable variant
   */
  @NonNls String GIT_SSH_VAR = "GIT_SSH_VARIANT";
  /**
   * Name of the handler
   */
  @NonNls String HANDLER_NAME = "Git4ideaSSHHandler";
  /**
   * Proxy settings
   */
  @NonNls String SSH_USE_PROXY_ENV = "GIT4IDEA_SSH_USE_PROXY";
  @NonNls String SSH_PROXY_HOST_ENV = "GIT4IDEA_SSH_PROXY_HOST";
  @NonNls String SSH_PROXY_PORT_ENV = "GIT4IDEA_SSH_PROXY_PORT";
  @NonNls String SSH_PROXY_AUTHENTICATION_ENV = "GIT4IDEA_SSH_PROXY_AUTHENTICATION";
  @NonNls String SSH_PROXY_USER_ENV = "GIT4IDEA_SSH_PROXY_USER";
  @NonNls String SSH_PROXY_PASSWORD_ENV = "GIT4IDEA_SSH_PROXY_PASSWORD";
  /**
   * Verify server host key
   *
   * @param token                    Access token.
   * @param hostName                 a host name
   * @param port                     a port number
   * @param serverHostKeyAlgorithm   an algorithm
   * @param serverHostKeyFingerprint a key fingerprint
   * @param isNew                    true if the key is a new, false if the key was changed
   * @return true the host is verified, false otherwise
   */
  boolean verifyServerHostKey(String token,
                              String hostName,
                              int port,
                              String serverHostKeyAlgorithm,
                              String serverHostKeyFingerprint,
                              boolean isNew);

  /**
   * Ask passphrase for the key
   *
   * @param token       Access token.
   * @param userName      a name of user
   * @param keyPath       a path for the key
   * @param resetPassword a reset password if one was stored in password database
   * @param lastError     a last error (or empty string)
   * @return the passphrase entered by the user
   */
  @Nullable
  String askPassphrase(String token, final String userName, final String keyPath, boolean resetPassword, final String lastError);

  /**
   * Reply to challenge for keyboard-interactive method. Also used for
   *
   * @param token   Access token.
   * @param userName    a user name (includes host and port)
   * @param name        name of challenge
   * @param instruction instruction
   * @param numPrompts  amount of prompts
   * @param prompt      prompts
   * @param echo        whether the reply should be echoed (boolean values represented as string due to XML RPC limitation)
   * @param lastError   the last error from the challenge
   * @return a list or replies to challenges (the size should be equal to the number of prompts)
   */
  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  @Nullable
  Vector<String> replyToChallenge(String token,
                                  final String userName,
                                  final String name,
                                  final String instruction,
                                  final int numPrompts,
                                  final Vector<String> prompt,
                                  final Vector<Boolean> echo,
                                  final String lastError);

  /**
   * Ask password for the specified user name
   *
   * @param token         Access token.
   * @param userName      a name of user to ask password for
   * @param resetPassword a reset password if one was stored in password database
   * @param lastError     a last error
   * @return the password or null if authentication failed.
   */
  @Nullable
  String askPassword(String token, final String userName, boolean resetPassword, final String lastError);

  /**
   * Notify invoker about last successful authentication attempt.
   *
   * @param token the handler
   * @param userName  the user name
   * @param method    the authentication method, the empty string means that authentication failed
   * @param error     the error shown in the case when authentication process failed
   *
   * @return The method doesn't return any sensible value, but it is needed here, since the Apache XML-RPC implementation which we use
   *         doesn't allow void methods: "IllegalArgumentException: void return types for handler methods not supported".
   */
  String setLastSuccessful(String token, final String userName, final String method, final String error);

  /**
   * Get last successful authentication method
   *
   * @param token Access token
   * @param userName  the user name
   * @return the authentication method, the empty string means that last authentication failed
   */
  String getLastSuccessful(String token, final String userName);
}
