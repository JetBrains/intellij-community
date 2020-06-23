// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.i18n.GithubBundle;
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
  @NotNull private final Convertor<? super String, ? extends ForkInfo> myCheckFork;
  private ForkInfo mySelectedFork;


  public GithubSelectForkDialog(@NotNull Project project,
                                @Nullable List<GHRepositoryPath> forks,
                                @NotNull Convertor<? super String, ? extends ForkInfo> checkFork) {
    super(project);
    myProject = project;
    myCheckFork = checkFork;

    myPanel = new GithubSelectForkPanel();

    if (forks != null) {
      myPanel.setUsers(ContainerUtil.map(forks, GHRepositoryPath::getOwner));
    }

    setTitle(GithubBundle.message("select.fork.dialog.title"));
    init();
  }

  @Override
  protected void doOKAction() {
    ForkInfo fork = myCheckFork.convert(myPanel.getUser());
    if (fork == null) {
      GithubNotifications.showErrorDialog(myProject, GithubBundle.message("cannot.find.repository"),
                                          GithubBundle.message("cannot.find.fork"));
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
