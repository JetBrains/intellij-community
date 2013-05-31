package com.intellij.execution;

import java.util.concurrent.Future;

/**
 * @author traff
 */
public interface TaskExecutor {
  Future<?> executeTask(Runnable task);
}
