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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubNotifications;

import javax.swing.*;
import java.util.List;

import static org.jetbrains.plugins.github.GithubCreatePullRequestWorker.ForkInfo;

/**
 * @author Aleksey Pivovarov
 */
public class GithubSelectForkDialog extends DialogWrapper {
  @NotNull private final GithubSelectForkPanel myPanel;
  @NotNull private final Project myProject;
  @NotNull private final Convertor<String, ForkInfo> myCheckFork;
  private ForkInfo mySelectedFork;


  public GithubSelectForkDialog(@NotNull Project project,
                                @Nullable List<GithubFullPath> forks,
                                @NotNull Convertor<String, ForkInfo> checkFork) {
    super(project);
    myProject = project;
    myCheckFork = checkFork;

    myPanel = new GithubSelectForkPanel();

    if (forks != null) {
      myPanel.setUsers(ContainerUtil.map(forks, new Function<GithubFullPath, String>() {
        @Override
        public String fun(GithubFullPath path) {
          return path.getUser();
        }
      }));
    }

    setTitle("Select Base Fork Repository");
    init();
  }

  @Override
  protected void doOKAction() {
    ForkInfo fork = myCheckFork.convert(myPanel.getUser());
    if (fork == null) {
      GithubNotifications.showErrorDialog(myProject, "Can't Find Repository", "Can't find fork for selected user");
    }
    else {
      mySelectedFork = fork;
      super.doOKAction();
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  @NotNull
  public ForkInfo getPath() {
    return mySelectedFork;
  }
}
