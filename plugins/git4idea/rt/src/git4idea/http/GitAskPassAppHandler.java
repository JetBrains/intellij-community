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
package git4idea.http;

import externalApp.ExternalAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This handler is called by {@link GitAskPassApp} when Git requests user credentials.
 */
public interface GitAskPassAppHandler extends ExternalAppHandler {

  @NonNls String IJ_ASK_PASS_HANDLER_ENV = "INTELLIJ_GIT_ASKPASS_HANDLER";
  @NonNls String IJ_ASK_PASS_PORT_ENV = "INTELLIJ_GIT_ASKPASS_PORT";
  @NonNls String ENTRY_POINT_NAME = "gitAskPass";

  /**
   * Get the answer for interactive input request from git.
   *
   * @param arg Argument of the input script
   * @return user input
   */
  @NotNull
  String handleInput(@NotNull String arg);
}
