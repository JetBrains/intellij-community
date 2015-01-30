package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.rpc.CommandProcessor;

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
    if (!(error instanceof Promise.MessageError)) {
      CommandProcessor.LOG.error(error);
    }
    session.reportError((description == null ? "" : description + ": ") + error.getMessage());
  }
}