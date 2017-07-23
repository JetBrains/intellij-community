/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static java.awt.EventQueue.isDispatchThread;

/**
 * @author Sergey.Malenkov
 */
final class Reference<T> {
  private static final Logger LOG = Logger.getInstance(Reference.class);
  private volatile boolean valid;
  private volatile T value;

  boolean isValid() {
    return valid;
  }

  void invalidate() {
    valid = false;
  }

  T set(T value) {
    T old = this.value;
    this.value = value;
    valid = true;
    return old;
  }

  T get() {
    return value;
  }

  static <T> Reference<T> create(@NotNull Supplier<T> supplier) {
    Reference<T> reference = new Reference<>();
    try {
      Runnable process = () -> reference.set(supplier.get());
      ProgressManager manager = getProgressManager();
      if (manager == null || isDispatchThread()) {
        process.run();
      }
      else {
        manager.runInReadActionWithWriteActionPriority(process, null);
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (Exception exception) {
      LOG.warn(exception);
      return null;
    }
    return reference;
  }

  private static ProgressManager getProgressManager() {
    try {
      return ProgressManager.getInstance();
    }
    catch (NullPointerException exception) {
      LOG.debug("progress manager is not available");
      return null; // in tests without application
    }
  }
}
