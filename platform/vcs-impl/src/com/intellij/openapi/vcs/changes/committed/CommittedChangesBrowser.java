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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class CommittedChangesBrowser extends SimpleChangesBrowser {
  private CommittedChangesBrowserUseCase myUseCase;

  public CommittedChangesBrowser(Project project) {
    super(project, false, false);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserToolbar")
    );
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction("Vcs.RepositoryChangesBrowserMenu")
    );
  }

  public void setUseCase(final CommittedChangesBrowserUseCase useCase) {
    myUseCase = useCase;
  }

  public Object getData(@NonNls final String dataId) {
    if (CommittedChangesBrowserUseCase.DATA_KEY.is(dataId)) {
      return myUseCase;
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      Set<AbstractVcs> abstractVcs = ChangesUtil.getAffectedVcses(getSelectedChanges(), myProject);
      if (abstractVcs.size() == 1) return ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(abstractVcs)).getKeyInstanceMethod();
      return null;
    }
    return super.getData(dataId);
  }
}
