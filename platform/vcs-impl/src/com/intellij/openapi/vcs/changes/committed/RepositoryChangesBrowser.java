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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.changes.actions.RevertSelectedChangesAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class RepositoryChangesBrowser extends ChangesBrowser implements DataProvider {
  private CommittedChangesBrowserUseCase myUseCase;

  public RepositoryChangesBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(project, changeLists, Collections.<Change>emptyList(), null, false, false, null, MyUseCase.COMMITTED_CHANGES);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    super(project, changeLists, changes, initialListSelection, false, false, null, MyUseCase.COMMITTED_CHANGES);
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(new ShowDiffWithLocalAction());
    final MyEditSourceAction editSourceAction = new MyEditSourceAction();
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    toolBarGroup.add(editSourceAction);
    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    toolBarGroup.add(action);
    final RevertSelectedChangesAction revertSelectedChangesAction = new RevertSelectedChangesAction();
    toolBarGroup.add(revertSelectedChangesAction);

    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("RepositoryChangesBrowserToolbar");
    final AnAction[] actions = group.getChildren(null);
    for (AnAction anAction : actions) {
      toolBarGroup.add(anAction);
    }
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  public Object getData(@NonNls final String dataId) {
    if (CommittedChangesBrowserUseCase.CONTEXT_NAME.equals(dataId)) {
      return myUseCase;
    }

    else if (VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      final List<Change> list = myViewer.getSelectedChanges();
      return list.toArray(new Change [list.size()]);
    } else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      final Change highestSelection = myViewer.getHighestLeadSelection();
      return (highestSelection == null) ? new Change[]{} : new Change[] {highestSelection};
    } else {
      final TypeSafeDataProviderAdapter adapter = new TypeSafeDataProviderAdapter(this);
      return adapter.getData(dataId);
    }
  }

  private class MyEditSourceAction extends EditSourceAction {
    private final Icon myEditSourceIcon;

    public MyEditSourceAction() {
      myEditSourceIcon = IconLoader.getIcon("/actions/editSource.png");
    }

    public void update(final AnActionEvent event) {
      super.update(event);
      event.getPresentation().setIcon(myEditSourceIcon);
      event.getPresentation().setText("Edit Source");
      if ((! ModalityState.NON_MODAL.equals(ModalityState.current())) ||
          CommittedChangesBrowserUseCase.IN_AIR.equals(event.getDataContext().getData(CommittedChangesBrowserUseCase.CONTEXT_NAME))) {
        event.getPresentation().setEnabled(false);
      }
    }

    protected Navigatable[] getNavigatables(final DataContext dataContext) {
      Change[] changes = (Change[])dataContext.getData(VcsDataKeys.SELECTED_CHANGES.getName());
      if (changes != null) {
        Collection<Change> changeCollection = Arrays.asList(changes);
        return ChangesUtil.getNavigatableArray(myProject, ChangesUtil.getFilesFromChanges(changeCollection));
      }
      return null;
    }
  }
}
