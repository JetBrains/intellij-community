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
package org.jetbrains.git4idea.http;

import org.jetbrains.annotations.NotNull;

/**
 * This handler is called via XML RPC from {@link GitAskPassApp} when Git requests user credentials.
 *
 * @author Kirill Likhodedov
 */
public interface GitAskPassXmlRpcHandler {

  String GIT_ASK_PASS_ENV = "GIT_ASKPASS";
  String GIT_ASK_PASS_HANDLER_ENV = "GIT_ASKPASS_HANDLER";
  String GIT_ASK_PASS_PORT_ENV = "GIT_ASKPASS_PORT";
  String HANDLER_NAME = GitAskPassXmlRpcHandler.class.getName();

  /**
   * Get the username from the user to access the given URL.
   * @param token   Access token.
   * @param url     URL which Git tries to access.
   * @return The Username which should be used for the URL.
   */
  // UnusedDeclaration suppressed: the method is used via XML RPC
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  String askUsername(String token, @NotNull String url);

  /**
   * Get the password from the user to access the given URL.
   * It is assumed that the username either is specified in the URL (http://username@host.com), or has been asked earlier.
   * @param token   Access token.
   * @param url     URL which Git tries to access.
   * @return The password which should be used for the URL.
   */
  // UnusedDeclaration suppressed: the method is used via XML RPC
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  String askPassword(String token, @NotNull String url);

}
