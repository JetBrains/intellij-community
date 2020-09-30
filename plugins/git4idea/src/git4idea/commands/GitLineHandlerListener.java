// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
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
  @Override
  @SuppressWarnings({"UnnecessaryFullyQualifiedName"})
  void onLineAvailable(@NlsSafe String line, Key outputType);
}
