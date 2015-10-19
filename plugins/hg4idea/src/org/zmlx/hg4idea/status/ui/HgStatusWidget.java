/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.status.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.branch.HgBranchPopup;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * Widget to display basic hg status in the status bar.
 */
public class HgStatusWidget extends DvcsStatusWidget<HgRepository> {

  @NotNull private final HgVcs myVcs;
  @NotNull private final HgProjectSettings myProjectSettings;

  public HgStatusWidget(@NotNull HgVcs vcs, @NotNull Project project, @NotNull HgProjectSettings projectSettings) {
    super(project, "Hg");
    myVcs = vcs;
    myProjectSettings = projectSettings;
  }

  @Override
  public StatusBarWidget copy() {
    return new HgStatusWidget(myVcs, ObjectUtils.assertNotNull(getProject()), myProjectSettings);
  }

  @Nullable
  @Override
  protected HgRepository guessCurrentRepository(@NotNull Project project) {
    return DvcsUtil.guessCurrentRepositoryQuick(project, HgUtil.getRepositoryManager(project),
                                                HgProjectSettings.getInstance(project).getRecentRootPath());
  }

  @NotNull
  @Override
  protected String getFullBranchName(@NotNull HgRepository repository) {
    return HgUtil.getDisplayableBranchOrBookmarkText(repository);
  }

  @Override
  protected boolean isMultiRoot(@NotNull Project project) {
    return HgUtil.getRepositoryManager(project).moreThanOneRoot();
  }

  @NotNull
  @Override
  protected ListPopup getPopup(@NotNull Project project, @NotNull HgRepository repository) {
    return HgBranchPopup.getInstance(project, repository).asListPopup();
  }

  @Override
  protected void subscribeToRepoChangeEvents(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(HgVcs.STATUS_TOPIC, new HgUpdater() {
      @Override
      public void update(Project project, @Nullable VirtualFile root) {
        updateLater();
      }
    });
  }

  @Override
  protected void rememberRecentRoot(@NotNull String path) {
    myProjectSettings.setRecentRootPath(path);
  }
}
