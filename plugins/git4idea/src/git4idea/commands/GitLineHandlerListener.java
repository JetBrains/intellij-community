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
package git4idea.commands;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineProcessEventListener;

/**
 * Listener for line events
 */
public interface GitLineHandlerListener extends LineProcessEventListener {
  /**
   * This method is invoked when line (as separated by \n or \r) becomes available.
   *
   * @param line       a line of the text
   * @param outputType a type of output (one of constants from {@link com.intellij.execution.process.ProcessOutputTypes})
   */
  @SuppressWarnings({"UnusedParameters", "UnnecessaryFullyQualifiedName"})
  void onLineAvailable(String line, Key outputType);
}
