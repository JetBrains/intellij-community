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

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * {@link VcsDataKeys#CHANGES_SELECTION}
 */
public class VcsChangesSelectionRule implements GetDataRule {
  @Nullable
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    return getChangesSelection(dataProvider);
  }

  @Nullable
  public ListSelection<Change> getChangesSelection(@NotNull DataProvider dataProvider) {
    Change[] selectedChanges = VcsDataKeys.SELECTED_CHANGES.getData(dataProvider);
    if (selectedChanges != null) {
      return ListSelection.createAt(Arrays.asList(selectedChanges), 0)
        .asExplicitSelection();
    }

    Change[] changes = VcsDataKeys.CHANGES.getData(dataProvider);
    if (changes != null) {
      return ListSelection.createAt(Arrays.asList(changes), 0);
    }

    return null;
  }
}
