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

import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.mock.MockLocalFileSystem
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.MockChangeListManager
import git4idea.Notificator
import git4idea.PlatformFacade
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
/**
 * 
 * @author Kirill Likhodedov
 */
public class GitTestPlatformFacade implements PlatformFacade {

  private GitMockVcsManager myVcsManager;
  private GitMockVcs myVcs;
  private TestNotificator myNotificator;
  private TestDialogManager myTestDialogManager;
  private GitMockProjectRootManager myProjectRootManager;
  private ChangeListManagerEx myChangeListManager;
  private GitTestRepositoryManager myRepositoryManager;
  private MockVcsHelper myVcsHelper;

  public GitTestPlatformFacade() {
    myTestDialogManager = new TestDialogManager();
    myProjectRootManager = new GitMockProjectRootManager();
    myChangeListManager = new MockChangeListManager();
    myRepositoryManager = new GitTestRepositoryManager();
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
  @Override
  public ProjectRootManager getProjectRootManager(@NotNull Project project) {
    return myProjectRootManager;
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computable) {
    return computable.compute();
  }

  @Override
  public void runReadAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void runWriteAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    runnable.run();
  }

  @Override
  public ChangeListManagerEx getChangeListManager(@NotNull Project project) {
    return myChangeListManager;
  }

  @Override
  public LocalFileSystem getLocalFileSystem() {
    return new MockLocalFileSystem();
  }

  @NotNull
  @Override
  public AbstractVcsHelper getVcsHelper(@NotNull Project project) {
    if (myVcsHelper == null) {
      myVcsHelper = new MockVcsHelper();
    }
    return myVcsHelper;
  }

  @NotNull
  @Override
  public GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return myRepositoryManager;
  }

  @Nullable
  @Override
  public IdeaPluginDescriptor getPluginByClassName(@NotNull String name) {
    return null;
  }

  @NotNull
  @Override
  public String getLineSeparator(@NotNull VirtualFile file, boolean detect) {
    try {
      return FileUtil.loadFile(new File(file.getPath())).contains("\r\n") ? "\r\n" : "\n";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  public GitVcsSettings getSettings(Project project) {
    return new GitVcsSettings(new GitVcsApplicationSettings());
  }

  @Override
  public void saveAllDocuments() {
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFileByPath(@NotNull String path) {
    return new MockVirtualFile(path);
  }

  @NotNull
  @Override
  public ProjectManagerEx getProjectManager() {
    [
            blockReloadingProjectOnExternalChanges: {},
            unblockReloadingProjectOnExternalChanges: {}
    ] as ProjectManagerEx
  }

  @NotNull
  @Override
  SaveAndSyncHandler getSaveAndSyncHandler() {
    [
            blockSaveOnFrameDeactivation: {},
            blockSyncOnFrameActivation: {},
            unblockSaveOnFrameDeactivation: {},
            unblockSyncOnFrameActivation: {}
    ] as SaveAndSyncHandler
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
  public TestDialogManager getDialogManager() {
    return myTestDialogManager;
  }

}
