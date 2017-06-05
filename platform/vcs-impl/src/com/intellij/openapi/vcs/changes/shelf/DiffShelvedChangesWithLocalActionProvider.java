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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import org.jetbrains.annotations.NotNull;

public class DiffShelvedChangesWithLocalActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY) != null ||
           e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setDescription("Compare shelved version with current");
    e.getPresentation().setEnabled(DiffShelvedChangesActionProvider.isEnabled(e.getDataContext()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffShelvedChangesActionProvider.showShelvedChangesDiff(e.getDataContext(), true);
  }
}
