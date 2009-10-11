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

/**
 * An adapter for line handler
 */
public class GitLineHandlerAdapter implements GitLineHandlerListener {
  /**
   * {@inheritDoc}
   */
  public void onLineAvailable(final String line, final Key outputType) {
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  public void processTerminated(final int exitCode) {
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  public void startFailed(final Throwable exception) {
    // do nothing
  }
}
