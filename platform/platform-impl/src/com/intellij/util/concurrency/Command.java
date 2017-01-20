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
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Sergey.Malenkov
 */
public interface Command<T> extends Supplier<T>, Consumer<T> {
  final class Processor {
    public final Invoker foreground;
    public final Invoker background;

    public Processor(@NotNull Invoker foreground, @NotNull Invoker background) {
      this.foreground = foreground;
      this.background = background;
    }

    /**
     * Lets the specified consumer to accept the given value on the foreground thread.
     */
    public <T> void consume(Consumer<T> consumer, T value) {
      if (consumer != null) foreground.invokeLaterIfNeeded(() -> consumer.accept(value));
    }

    /**
     * Lets the specified command to produce a value on the background thread
     * and to accept this value on the foreground thread.
     */
    public <T> void process(Command<T> command) {
      if (command != null) background.invokeLaterIfNeeded(() -> consume(command, command.get()));
    }

    /**
     * Lets the specified supplier to produce a value on the background thread
     * and the specified consumer to accept this value on the foreground thread.
     */
    public <T> void process(Supplier<T> supplier, Consumer<T> consumer) {
      if (supplier != null) {
        background.invokeLaterIfNeeded(() -> consume(consumer, supplier.get()));
      }
      else {
        consume(consumer, null);
      }
    }

    /**
     * Returns a workload of both task queues.
     *
     * @return amount of tasks, which are executing or waiting for execution
     */
    public int getTaskCount() {
      return foreground.getTaskCount() + background.getTaskCount();
    }
  }
}
