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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsImport.ImportDetails;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.experts.importToCvs.ImportWizard;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

public class ImportAction extends ActionOnSelectedElement {
  private ImportDetails myImportDetails;

  public ImportAction() {
    super(false);
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.import");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    final VirtualFile selectedFile = context.getSelectedFile();
    final ImportWizard importWizard = new ImportWizard(context.getProject(), selectedFile);
    if (!importWizard.showAndGet()) {
      return CvsHandler.NULL;
    }

    myImportDetails = importWizard.createImportDetails();
    if (myImportDetails == null) return CvsHandler.NULL;
    return CommandCvsHandler.createImportHandler(myImportDetails);
  }

  protected void onActionPerformed(CvsContext context, CvsTabbedWindow tabbedWindow, boolean successfully, CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    final ImportConfiguration importConfiguration = ImportConfiguration.getInstance();
    if (successfully && importConfiguration.CHECKOUT_AFTER_IMPORT) {
      createCheckoutAction(importConfiguration.MAKE_NEW_FILES_READ_ONLY).actionPerformed(context);
    }
  }

  private AbstractAction createCheckoutAction(final boolean makeNewFilesReadOnly) {
    return new AbstractAction(false) {
      protected String getTitle(VcsContext context) {
        return CvsBundle.message("operation.name.check.out.project");
      }

      protected CvsHandler getCvsHandler(CvsContext context) {
        final Project project = context.getProject();
        return CommandCvsHandler.createCheckoutHandler(myImportDetails.getCvsRoot(),
                                                       new String[]{myImportDetails.getModuleName()},
                                                       myImportDetails.getBaseImportDirectory(),
                                                       true, makeNewFilesReadOnly,
                                                       project == null ? null : VcsConfiguration.getInstance(project).getCheckoutOption());
      }

      protected void onActionPerformed(CvsContext context, CvsTabbedWindow tabbedWindow, boolean successfully, CvsHandler handler) {
        super.onActionPerformed(context, tabbedWindow, successfully, handler);
        final Project project = context.getProject();
        if (successfully) {
          if (project != null) {
            final VirtualFile importedRoot = CvsVfsUtil.findFileByIoFile(myImportDetails.getBaseImportDirectory());
            updateDirectoryMappings(project, importedRoot);
          }
        }
      }

      /**
       * Basically copied from GitInit/HgInit
       */
      private void updateDirectoryMappings(Project project , VirtualFile mapRoot) {
        if (project == null || project.isDefault()) {
          return;
        }
        final VirtualFile projectBaseDir = project.getBaseDir();
        if (projectBaseDir == null || !VfsUtil.isAncestor(projectBaseDir, mapRoot, false)) {
          return;
        }
        mapRoot.refresh(false, false);
        final String path = mapRoot.equals(projectBaseDir) ? "" : mapRoot.getPath();
        ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
        manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), path, CvsVcs2.getInstance(project).getName()));
        manager.updateActiveVcss();
      }
    };
  }
}
