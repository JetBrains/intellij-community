/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HgPromptHandler is used by {@link HgPromptCommandExecutor, when you want to change the behavior of
 * standard commands execution in the Mercurial.
 *
 * @author Nadya Zabrodina
 */
public interface HgPromptHandler {

  /**
   * Checks you need to change the default behavior.
   *
   * @param message standard output message from Mercurial
   * @return
   */
  boolean shouldHandle(@Nullable String message);

  /**
   * Change default behavior in commands execution. Execute only if shouldHandle method return true.
   *
   * @param message       standard output message from Mercurial
   * @param choices       possible choices
   * @param defaultChoice
   * @return
   */
  HgPromptChoice promptUser(@NotNull final String message,
                            @NotNull final HgPromptChoice[] choices,
                            @NotNull final HgPromptChoice defaultChoice);
}
