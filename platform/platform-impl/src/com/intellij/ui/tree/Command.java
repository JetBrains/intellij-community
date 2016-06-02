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
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Sergey.Malenkov
 */
interface Command<T> extends Supplier<T>, Consumer<T> {
  final class Processor implements Disposable {
    public final Invoker foreground;
    public final Invoker background;

    public Processor(@NotNull @NonNls String name, boolean queueInBackground) {
      this(new Invoker.EDT(name), queueInBackground ? new Invoker.BackgroundQueue(name) : new Invoker.Background(name));
    }

    public Processor(@NotNull Invoker foreground, @NotNull Invoker background) {
      this.foreground = foreground;
      this.background = background;
    }

    public <T> void consume(Consumer<T> consumer, T value) {
      if (consumer != null) foreground.invokeLaterIfNeeded(() -> consumer.accept(value));
    }

    public <T> void process(Command<T> command) {
      if (command != null) background.invokeLaterIfNeeded(() -> consume(command, command.get()));
    }

    public <T> void process(Supplier<T> supplier, Consumer<T> consumer) {
      if (supplier != null) {
        background.invokeLaterIfNeeded(() -> consume(consumer, supplier.get()));
      }
      else {
        consume(consumer, null);
      }
    }

    @Override
    public void dispose() {
      Disposer.dispose(foreground);
      Disposer.dispose(background);
    }
  }
}
