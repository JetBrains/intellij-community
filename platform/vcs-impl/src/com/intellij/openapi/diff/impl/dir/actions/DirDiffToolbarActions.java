/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffToolbarActions extends ActionGroup {
  private final AnAction[] myActions;
  private final DirDiffTableModel myModel;

  public DirDiffToolbarActions(DirDiffTableModel model) {
    super("Directory Diff Actions", false);
    myModel = model;
    myActions = new AnAction[] {
      new RefreshDirDiffAction(myModel),
      Separator.getInstance(),
      new EnableLeft(myModel),
      new EnableNotEqual(myModel),
      new EnableEqual(myModel),
      new EnableRight(myModel),
      Separator.getInstance(),
      new ChangeCompareModeGroup(myModel),
      Separator.getInstance()
    };
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myActions;
  }
}
