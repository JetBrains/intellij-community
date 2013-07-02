/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.dvcs.test.DvcsTestPlatformFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitPlatformFacade
import git4idea.Notificator
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import org.jetbrains.annotations.NotNull
/**
 *
 * @author Kirill Likhodedov
 */
class GitTestPlatformFacade extends DvcsTestPlatformFacade implements GitPlatformFacade {

  private static final GitVcsApplicationSettings ourAppSettings = new GitVcsApplicationSettings()

  private GitMockVcs myVcs;
  private TestNotificator myNotificator;
  private TestDialogManager myTestDialogManager;
  private GitTestRepositoryManager myRepositoryManager;
  private GitMockVcsManager myVcsManager;
  private GitVcsSettings mySettings

  GitTestPlatformFacade() {
    myRepositoryManager = new GitTestRepositoryManager();
    myTestDialogManager = new TestDialogManager();
  }

  @NotNull
  @Override
  public ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    if (myVcsManager == null) {
      myVcsManager = new GitMockVcsManager(project, this);
    }
    return myVcsManager;
  }

  @NotNull
  @Override
  public Notificator getNotificator(@NotNull Project project) {
    if (myNotificator == null) {
      myNotificator = new TestNotificator(project);
    }
    return myNotificator;
  }

  @NotNull
  @Override
  public GitVcsSettings getSettings(Project project) {
    if (mySettings == null) {
      mySettings = new GitVcsSettings(ourAppSettings);
    }
    return mySettings;
  }

  @NotNull
  @Override
  public AbstractVcs getVcs(@NotNull Project project) {
    if (myVcs == null) {
      myVcs = new GitMockVcs(project);
    }
    return myVcs;
  }

  @NotNull
  @Override
  public GitTestRepositoryManager getRepositoryManager(@NotNull Project project) {
    return myRepositoryManager;
  }

  @Override
  public void showDialog(@NotNull DialogWrapper dialog) {
    try {
      myTestDialogManager.show(dialog);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public TestDialogManager getDialogManager() {
    return myTestDialogManager;
  }

}
