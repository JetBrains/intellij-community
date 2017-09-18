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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class AlienChangeListBrowser extends ChangesBrowser {
  private final List<Change> myChanges;

  public AlienChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                final ChangeList initialListSelection, final boolean capableOfExcludingChanges,
                                final boolean highlightProblems) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems, null, MyUseCase.LOCAL_CHANGES, null);
    myChanges = changes;
    rebuildList();
  }

  @Override
  public void rebuildList() {
    // dont change lists
    myViewer.setChangesToDisplay(myChanges ==  null ? Collections.emptyList() : myChanges);
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    if (! changeLists.isEmpty()) {
      mySelectedChangeList = changeLists.get(0);
    }
  }

  @Override
  protected void buildToolBar(DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(ActionManager.getInstance().getAction("AlienCommitChangesDialog.AdditionalActions"));
  }

  @Override
  @NotNull
  public List<Change> getCurrentIncludedChanges() {
    return ContainerUtil.newArrayList(myChanges);
  }
}
