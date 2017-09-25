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

import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class RepositoryChangesBrowser extends ChangesBrowser implements DataProvider {

  private CommittedChangesBrowserUseCase myUseCase;

  @Deprecated
  public RepositoryChangesBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    this(project);
  }

  @Deprecated
  public RepositoryChangesBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                  final ChangeList initialListSelection) {
    this(project, initialListSelection, null);
  }


  public RepositoryChangesBrowser(@NotNull Project project) {
    this(project, null, null);
  }

  public RepositoryChangesBrowser(@NotNull Project project,
                                  @Nullable ChangeList initialListSelection,
                                  @Nullable VirtualFile toSelect) {
    super(project, null, Collections.emptyList(), initialListSelection, false, false, null, MyUseCase.COMMITTED_CHANGES, toSelect);
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar"));
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
}
