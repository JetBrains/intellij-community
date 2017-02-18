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
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.ui.MigrateRootDialog;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import org.netbeans.lib.cvsclient.file.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MigrateCvsRootAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.MigrateCvsRootAction");
  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public MigrateCvsRootAction() {
    super();
    myVisibility.shouldNotBePerformedOnFile();
    myVisibility.addCondition(ActionOnSelectedElement.FILES_ARE_UNDER_CVS);
  }

  @Override
  public void update(AnActionEvent e) {
    myVisibility.applyToEvent(e);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final VcsContext context = CvsContextWrapper.createInstance(event);
    final VirtualFile selectedFile = context.getSelectedFile();
    final Project project = context.getProject();
    final MigrateRootDialog dialog = new MigrateRootDialog(project, selectedFile);
    if (!dialog.showAndGet()) {
      return;
    }
    final File directory = dialog.getSelectedDirectory();
    final boolean shouldReplaceAllRoots = dialog.shouldReplaceAllRoots();
    final List<File> rootFiles = new ArrayList<>();
    try {
      if (shouldReplaceAllRoots) {
        collectRootFiles(directory, null, rootFiles);
      }
      else {
        collectRootFiles(directory, dialog.getCvsRoot(), rootFiles);
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    final CvsRootConfiguration cvsConfiguration = dialog.getSelectedCvsConfiguration();
    final String cvsRoot = cvsConfiguration.getCvsRootAsString();
    for (final File file : rootFiles) {
      try {
        FileUtils.writeLine(file, cvsRoot);
      }
      catch (IOException e) {
        LOG.error(e);
        break;
      }
    }
    final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      for (File file : rootFiles) {
        CvsVfsUtil.findFileByIoFile(file).refresh(true, false);
      }
    }
    finally {
      token.finish();
    }
    StatusBar.Info.set("Finished migrating CVS root to " + cvsRoot, project);
  }



  private static void collectRootFiles(File directory, final String root, final List<File> rootFiles) throws IOException {
    final File rootFile = getRootFile(directory);
    if (rootFile != null) {
      rootFiles.add(rootFile);
    }
    try {
      final File[] files = directory.listFiles(file -> {
        if (!file.isDirectory()) {
          return false;
        }
        final File rootFile1 = getRootFile(file);
        if (!rootFile1.exists()) {
          return false;
        }
        if (root == null) {
          return true;
        }
        try {
          final String cvsRoot = FileUtils.readLineFromFile(rootFile1).trim();
          return root.equals(cvsRoot);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      for (File file : files) {
        collectRootFiles(file, root, rootFiles);
      }
    } catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException) cause;
      }
    }
  }

  private static File getRootFile(File directory) {
    return new File(directory, CvsUtil.CVS + '/' + CvsUtil.CVS_ROOT_FILE);
  }
}
