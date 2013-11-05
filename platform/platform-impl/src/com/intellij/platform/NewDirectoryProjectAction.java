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
package com.intellij.platform;

import com.intellij.ide.GeneralSettings;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenedCallback;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class NewDirectoryProjectAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(NewDirectoryProjectAction.class);

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    NewDirectoryProjectDialog dlg = new NewDirectoryProjectDialog(project);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    generateProject(project, dlg);
  }

  protected Object showSettings(DirectoryProjectGenerator generator, VirtualFile baseDir)
      throws ProcessCanceledException {
    return generator.showGenerationSettings(baseDir);
  }

  @Nullable
  protected Project generateProject(Project project, NewDirectoryProjectDialog dlg) {
    final DirectoryProjectGenerator generator = dlg.getProjectGenerator();
    final File location = new File(dlg.getNewProjectLocation());
    if (!location.exists() && !location.mkdirs()) {
      Messages.showErrorDialog(project, "Cannot create directory '" + location + "'", "Create Project");
      return null;
    }

    final VirtualFile baseDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location);
      }
    });
    LOG.assertTrue(baseDir != null, "Couldn't find '" + location + "' in VFS");
    baseDir.refresh(false, true);

    if (baseDir.getChildren().length > 0) {
      int rc = Messages.showYesNoDialog(project,
                                        "The directory '" + location +
                                        "' is not empty. Would you like to create a project from existing sources instead?",
                                        "Create New Project", Messages.getQuestionIcon());
      if (rc == 0) {
        return PlatformProjectOpenProcessor.getInstance().doOpenProject(baseDir, null, false);
      }
    }

    String generatorName = generator == null ? "empty" : ConvertUsagesUtil.ensureProperKey(generator.getName());
    UsageTrigger.trigger("NewDirectoryProjectAction." + generatorName);
    Object settings = null;
    if (generator != null) {
      try {
        settings = showSettings(generator, baseDir);
      }
      catch (ProcessCanceledException e1) {
        return null;
      }
    }
    GeneralSettings.getInstance().setLastProjectCreationLocation(location.getParent());
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
}
