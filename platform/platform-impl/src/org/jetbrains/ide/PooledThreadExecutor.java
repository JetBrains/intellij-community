package org.jetbrains.ide;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public final class PooledThreadExecutor implements Executor {
  public static final PooledThreadExecutor INSTANCE = new PooledThreadExecutor();

  private PooledThreadExecutor() {
  }

  @Override
  public void execute(@NotNull Runnable command) {
    ApplicationManager.getApplication().executeOnPooledThread(command);
  }
}