/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.projectImport.ProjectOpenedCallback;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class NewDirectoryProjectAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(NewDirectoryProjectAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    NewDirectoryProjectDialog dlg = new NewDirectoryProjectDialog(project);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      generateProject(project, dlg);
    }
  }

  @Nullable
  protected Project generateProject(Project project, NewDirectoryProjectDialog dlg) {
    final DirectoryProjectGenerator generator = dlg.getProjectGenerator();
    return doGenerateProject(project, dlg.getNewProjectLocation(), generator, new Function<VirtualFile, Object>() {
      @Override
      public Object fun(VirtualFile file) {
        return showSettings(generator, file);
      }
    });
  }

  public static Project doGenerateProject(@Nullable final Project project,
                                          @NotNull final String locationString,
                                          @Nullable final DirectoryProjectGenerator generator,
                                          @NotNull final Function<VirtualFile, Object> settingsComputable) {
    final File location = new File(FileUtil.toSystemDependentName(locationString));
    if (!location.exists() && !location.mkdirs()) {
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.getAbsolutePath());
      Messages.showErrorDialog(project, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return null;
    }

    final VirtualFile baseDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location);
      }
    });
    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return null;
    }
    baseDir.refresh(false, true);

    if (baseDir.getChildren().length > 0) {
      String message = ActionsBundle.message("action.NewDirectoryProject.not.empty", location.getAbsolutePath());
      int rc = Messages.showYesNoDialog(project, message, ActionsBundle.message("action.NewDirectoryProject.title"), Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        return PlatformProjectOpenProcessor.getInstance().doOpenProject(baseDir, null, false);
      }
    }

    String generatorName = generator == null ? "empty" : ConvertUsagesUtil.ensureProperKey(generator.getName());
    UsageTrigger.trigger("NewDirectoryProjectAction." + generatorName);

    Object settings = null;
    if (generator != null) {
      try {
        settings = settingsComputable.fun(baseDir);
      }
      catch (ProcessCanceledException e) {
        return null;
      }
    }
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());
    final Object finalSettings = settings;
    return PlatformProjectOpenProcessor.doOpenProject(baseDir, null, false, -1, new ProjectOpenedCallback() {
      @Override
      public void projectOpened(Project project, Module module) {
        if (generator != null) {
          generator.generateProject(project, baseDir, finalSettings, module);
        }
      }
    }, false);
  }

  protected Object showSettings(DirectoryProjectGenerator generator, VirtualFile baseDir) throws ProcessCanceledException {
    return generator.showGenerationSettings(baseDir);
  }
  
  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.CreateNewProject);
    }
  }
}
