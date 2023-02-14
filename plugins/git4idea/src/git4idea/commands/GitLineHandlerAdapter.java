// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.util.Key;

/**
 * @deprecated Use the {@link GitLineHandlerListener}.
 */
@Deprecated(forRemoval = true)
public class GitLineHandlerAdapter implements GitLineHandlerListener {
  @Override
  public void onLineAvailable(final String line, final Key outputType) {
  }
}
