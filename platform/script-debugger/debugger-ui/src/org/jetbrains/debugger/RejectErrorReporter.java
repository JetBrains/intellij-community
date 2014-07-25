package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RejectErrorReporter implements Consumer<String> {
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
  public void consume(@Nullable String error) {
    session.reportError((description == null ? "" : description + ": ") + (error == null ? "Unknown error" : error));
  }
}