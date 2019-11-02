// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    public <T> void consume(Consumer<? super T> consumer, T value) {
      if (consumer != null) foreground.invoke(() -> consumer.accept(value));
    }

    /**
     * Lets the specified command to produce a value on the background thread
     * and to accept this value on the foreground thread.
     */
    public <T> void process(Command<T> command) {
      if (command != null) background.compute(command).onSuccess(value -> consume(command, value));
    }

    /**
     * Lets the specified supplier to produce a value on the background thread
     * and the specified consumer to accept this value on the foreground thread.
     */
    public <T> void process(Supplier<? extends T> supplier, Consumer<? super T> consumer) {
      if (supplier != null) {
        background.compute(supplier).onSuccess(value -> consume(consumer, value));
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
      if (foreground == background) return background.getTaskCount();
      return foreground.getTaskCount() + background.getTaskCount();
    }
  }
}
