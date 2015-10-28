/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.google.common.primitives.Ints;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChangeListUtil {

  @Nullable
  public static LocalChangeList getPredefinedChangeList(@NotNull String defaultName, @NotNull ChangeListManager changeListManager) {
    final LocalChangeList sameNamedList = changeListManager.findChangeList(defaultName);
    if (sameNamedList != null) return sameNamedList;
    LocalChangeList list = tryToMatchWithExistingChangelist(changeListManager, defaultName);
    return list == null ? changeListManager.getDefaultChangeList() : list;
  }

  @Nullable
  private static LocalChangeList tryToMatchWithExistingChangelist(@NotNull ChangeListManager changeListManager,
                                                                  @NotNull final String defaultName) {
    List<LocalChangeList> matched = ContainerUtil.findAll(changeListManager.getChangeListsCopy(), new Condition<LocalChangeList>() {
      @Override
      public boolean value(LocalChangeList list) {
        return defaultName.contains(list.getName().trim());
      }
    });

    return matched.isEmpty() ? null : Collections.max(matched, new Comparator<LocalChangeList>() {
      @Override
      public int compare(LocalChangeList o1, LocalChangeList o2) {
        return Ints.compare(o1.getName().trim().length(), o2.getName().trim().length());
      }
    });
  }
}
