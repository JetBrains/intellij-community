/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Runs all given runnables and throws all the caught exceptions at the end.
 *
 * @author peter
 */
public class RunAll implements Runnable {
  private final List<ThrowableRunnable<?>> myActions;

  @SafeVarargs
  public RunAll(@NotNull ThrowableRunnable<Throwable>... actions) {
    this(ContainerUtil.newArrayList(actions));
  }

  private RunAll(@NotNull List<ThrowableRunnable<?>> actions) {
    myActions = actions;
  }

  @SafeVarargs
  @Contract(pure=true)
  public final RunAll append(@NotNull ThrowableRunnable<Throwable>... actions) {
    return new RunAll(ContainerUtil.concat(myActions, ContainerUtil.newArrayList(actions)));
  }

  @Override
  public void run() {
    CompoundRuntimeException.throwIfNotEmpty(collectExceptions());
  }

  @NotNull
  public List<Throwable> collectExceptions() {
    List<Throwable> errors = new SmartList<>();
    for (ThrowableRunnable<?> action : myActions) {
      try {
        action.run();
      }
      catch (CompoundRuntimeException e) {
        errors.addAll(e.getExceptions());
      }
      catch (Throwable e) {
        errors.add(e);
      }
    }
    return errors;
  }
}
