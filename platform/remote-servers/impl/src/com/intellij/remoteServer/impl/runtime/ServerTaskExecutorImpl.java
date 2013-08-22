package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * @author nik
 */
public class ServerTaskExecutorImpl implements ServerTaskExecutor {
  private static final Logger LOG = Logger.getInstance(ServerTaskExecutorImpl.class);
  private final SequentialTaskExecutor myTaskExecutor;

  public ServerTaskExecutorImpl() {
    myTaskExecutor = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myTaskExecutor.execute(command);
  }

  @Override
  public void submit(@NotNull Runnable command) {
    execute(command);
  }

  @Override
  public void submit(@NotNull final ThrowableRunnable<?> command, @NotNull final RemoteOperationCallback callback) {
    execute(new Runnable() {
      @Override
      public void run() {
        try {
          command.run();
        }
        catch (Throwable e) {
          LOG.info(e);
          callback.errorOccurred(e.getMessage());
        }
      }
    });
  }
}
