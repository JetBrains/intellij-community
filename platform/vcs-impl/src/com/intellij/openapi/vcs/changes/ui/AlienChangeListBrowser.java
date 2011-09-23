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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;

import java.util.*;

public class AlienChangeListBrowser extends ChangesBrowser implements ChangesBrowserExtender {
  private final List<Change> myChanges;
  private final AbstractVcs myVcs;

  public AlienChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                final ChangeList initialListSelection, final boolean capableOfExcludingChanges,
                                final boolean highlightProblems, final AbstractVcs vcs) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems, null, MyUseCase.LOCAL_CHANGES, null);
    myChanges = changes;
    myVcs = vcs;
    rebuildList();
  }

  @Override
  public void rebuildList() {
    // dont change lists
    myViewer.setChangesToDisplay(myChanges ==  null ? Collections.<Change>emptyList() : myChanges);
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    if (! changeLists.isEmpty()) {
      mySelectedChangeList = changeLists.get(0);
    }
  }

  public void addToolbarActions(final DialogWrapper dialogWrapper) {
    final ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("AlienCommitChangesDialog.AdditionalActions");
    final AnAction[] children = group.getChildren(null);
    if (children != null) {
      for (AnAction anAction : children) {
        super.addToolbarAction(anAction);
      }
    }
  }

  public void addSelectedListChangeListener(final SelectedListChangeListener listener) {
    // does nothing - only one change list so far
  }

  public Collection<AbstractVcs> getAffectedVcses() {
    return Collections.singletonList(myVcs);
  }

  public List<Change> getCurrentIncludedChanges() {
    return new ArrayList<Change>(myChanges);
  }
}
