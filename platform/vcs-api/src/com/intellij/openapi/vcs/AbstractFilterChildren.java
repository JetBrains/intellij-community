/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.List;

public abstract class AbstractFilterChildren<T> {
  protected abstract void sortAscending(final List<T> list);
  protected abstract boolean isAncestor(final T parent, final T child);
  protected void onRemove(final T t) {
  }

  public void doFilter(final List<T> in) {
    sortAscending(in);

    for (int i = 1; i < in.size(); i++) {
      final T child = in.get(i);
      for (int j = i - 1; j >= 0; --j) {
        final T parent = in.get(j);
        if (isAncestor(parent, child)) {
          onRemove(child);
          in.remove(i);
          -- i;
          break;
        }
      }
    }
  }
}
