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
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/**
 * @author Irina.Chernushina on 9/23/2016.
 */
public interface MoveableParent<T> extends Parent<T> {
  void setParent(@NotNull T parent);

  static class Helper {
    public static <T extends MoveableParent> void treeSearch(@NotNull final T root,
                                                             @Nullable final Consumer<T> beforeChildrenCallback,
                                                             @Nullable final Consumer<T> afterChildrenCallback) {
      final ArrayDeque<Pair<Boolean, T>> queue = new ArrayDeque<Pair<Boolean, T>>();
      queue.add(Pair.create(true, root));
      while (!queue.isEmpty()) {
        final Pair<Boolean, T> pair = queue.removeFirst();
        final T line = pair.getSecond();
        if (Boolean.TRUE.equals(pair.getFirst())) {
          if (beforeChildrenCallback != null) beforeChildrenCallback.consume(line);
          final List<T> children = line.getChildren();
          if (!children.isEmpty()) {
            if (afterChildrenCallback != null) queue.addFirst(Pair.create(false, line));
            for (int i = children.size() - 1; i >= 0; i--) {
              queue.addFirst(Pair.create(true, children.get(i)));
            }
            continue;
          }
        }
        if (afterChildrenCallback != null) afterChildrenCallback.consume(line);
      }
    }
  }
}
