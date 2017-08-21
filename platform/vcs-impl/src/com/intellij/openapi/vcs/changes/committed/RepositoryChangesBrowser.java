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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction;
import com.intellij.openapi.vcs.changes.actions.RevertSelectedChangesAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffWithLocalAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getFiles;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;

/**
 * @author yole
 */
public class RepositoryChangesBrowser extends ChangesBrowser implements DataProvider {

  private CommittedChangesBrowserUseCase myUseCase;
  private EditSourceAction myEditSourceAction;

  public RepositoryChangesBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    this(project, changeLists, Collections.emptyList(), null);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    this(project, changeLists, changes, initialListSelection, null);
  }

  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection, VirtualFile toSelect) {
    super(project, changeLists, changes, initialListSelection, false, false, null, MyUseCase.COMMITTED_CHANGES, toSelect);
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(new ShowDiffWithLocalAction(true));
    toolBarGroup.add(new ShowDiffWithLocalAction(false));

    myEditSourceAction = new MyEditSourceAction();
    myEditSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    toolBarGroup.add(myEditSourceAction);
    OpenRepositoryVersionAction action = new OpenRepositoryVersionAction();
    toolBarGroup.add(action);
    final RevertSelectedChangesAction revertSelectedChangesAction = new RevertSelectedChangesAction();
    toolBarGroup.add(revertSelectedChangesAction);

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("RepositoryChangesBrowserToolbar");
    final AnAction[] actions = group.getChildren(null);
    for (AnAction anAction : actions) {
      toolBarGroup.add(anAction);
    }
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  public Object getData(@NonNls final String dataId) {
    if (CommittedChangesBrowserUseCase.DATA_KEY.is(dataId)) {
      return myUseCase;
    }

    else if (VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      final List<Change> list = myViewer.getSelectedChanges();
      return list.toArray(new Change[list.size()]);
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      final Change highestSelection = myViewer.getHighestLeadSelection();
      return (highestSelection == null) ? new Change[]{} : new Change[]{highestSelection};
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      Set<AbstractVcs> abstractVcs = ChangesUtil.getAffectedVcses(myViewer.getSelectedChanges(), myProject);
      if (abstractVcs.size() == 1) return ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(abstractVcs)).getKeyInstanceMethod();
      return null;
    }
    else {
      final TypeSafeDataProviderAdapter adapter = new TypeSafeDataProviderAdapter(this);
      return adapter.getData(dataId);
    }
  }

  public EditSourceAction getEditSourceAction() {
    return myEditSourceAction;
  }

  private class MyEditSourceAction extends EditSourceAction {
    private final Icon myEditSourceIcon;

    public MyEditSourceAction() {
      myEditSourceIcon = AllIcons.Actions.EditSource;
    }

    public void update(final AnActionEvent event) {
      super.update(event);
      event.getPresentation().setIcon(myEditSourceIcon);
      event.getPresentation().setText("Edit Source");
      if ((!ModalityState.NON_MODAL.equals(ModalityState.current())) ||
          CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(event.getDataContext()))) {
        event.getPresentation().setEnabled(false);
      }
    }

    protected Navigatable[] getNavigatables(final DataContext dataContext) {
      Change[] changes = VcsDataKeys.SELECTED_CHANGES.getData(dataContext);
      return changes != null ? getNavigatableArray(myProject, getFiles(Stream.of(changes))) : null;
    }
  }
}
