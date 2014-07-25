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
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

/**
 * Performs HTTP authentication, i. e. handles "ask username" and "ask password" requests from Git:
 *
 * @author Kirill Likhodedov
 */
public interface GitHttpAuthenticator {

  /**
   * Asks the password to access the specified URL.
   * @param url URL which needs authentication.
   * @return Password to access the URL.
   */
  @NotNull
  String askPassword(@NotNull String url);

  /**
   * Asks the username to access the specified URL. Password request will follow.
   * @param url URL which needs authentication, without username in it.
   * @return Username to access the URL.
   */
  @NotNull
  String askUsername(@NotNull String url);

  /**
   * Saves the entered username and password to the database for the future access.
   * This is called when authentication succeeds.
   */
  void saveAuthData();

  /**
   * Makes sure the entered password is removed from the database.
   * This is called when authentication fails.
   */
  void forgetPassword();

  /**
   * Checks if the authentication dialog was cancelled
   * (in which case the behavior might be different than if a wrong password was provided).
   */
  boolean wasCancelled();

}
