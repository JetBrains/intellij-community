// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.task;

import org.gradle.tooling.events.task.TaskExecutionResult;
import org.gradle.tooling.model.internal.Exceptions;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.util.List;

@ApiStatus.Internal
public abstract class InternalTaskExecutionDetails implements Serializable {
  private static final InternalTaskExecutionDetails UNSUPPORTED = new InternalTaskExecutionDetails() {
    @Override
    public boolean isIncremental() {
      throw Exceptions.unsupportedMethod(TaskExecutionResult.class.getSimpleName() + ".isIncremental()");
    }

    @Override
    public List<String> getExecutionReasons() {
      throw Exceptions.unsupportedMethod(TaskExecutionResult.class.getSimpleName() + ".getExecutionReasons()");
    }
  };

  public InternalTaskExecutionDetails() {
  }

  abstract boolean isIncremental();

  abstract List<String> getExecutionReasons();

  public static InternalTaskExecutionDetails of(final boolean incremental, final List<String> executionReasons) {
    return new InternalTaskExecutionDetails() {
      @Override
      public boolean isIncremental() {
        return incremental;
      }

      @Override
      public List<String> getExecutionReasons() {
        return executionReasons;
      }
    };
  }

  public static InternalTaskExecutionDetails unsupported() {
    return UNSUPPORTED;
  }
}
