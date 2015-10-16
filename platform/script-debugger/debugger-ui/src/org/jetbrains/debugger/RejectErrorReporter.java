/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.rpc.CommandProcessorKt;

public final class RejectErrorReporter implements Consumer<Throwable> {
  private final XDebugSession session;
  private final String description;

  public RejectErrorReporter(@NotNull XDebugSession session) {
    this(session, null);
  }

  public RejectErrorReporter(@NotNull XDebugSession session, @Nullable String description) {
    this.session = session;
    this.description = description;
  }

  @Override
  public void consume(Throwable error) {
    Promise.logError(CommandProcessorKt.getLOG(), error);
    if (error != AsyncPromise.OBSOLETE_ERROR) {
      session.reportError((description == null ? "" : description + ": ") + error.getMessage());
    }
  }
}