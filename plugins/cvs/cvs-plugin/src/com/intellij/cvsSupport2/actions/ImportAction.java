// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

public class ImportAction extends ActionOnSelectedElement {
  private ImportDetails myImportDetails;

  public ImportAction() {
    super(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.import");
  }

  @Override
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

  @Override
  protected void onActionPerformed(CvsContext context, CvsTabbedWindow tabbedWindow, boolean successfully, CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    final ImportConfiguration importConfiguration = ImportConfiguration.getInstance();
    if (successfully && importConfiguration.CHECKOUT_AFTER_IMPORT) {
      createCheckoutAction(importConfiguration.MAKE_NEW_FILES_READ_ONLY).actionPerformed(context);
    }
  }

  private AbstractAction createCheckoutAction(final boolean makeNewFilesReadOnly) {
    return new AbstractAction(false) {
      @Override
      protected String getTitle(VcsContext context) {
        return CvsBundle.message("operation.name.check.out.project");
      }

      @Override
      protected CvsHandler getCvsHandler(CvsContext context) {
        final Project project = context.getProject();
        return CommandCvsHandler.createCheckoutHandler(myImportDetails.getCvsRoot(),
                                                       new String[]{myImportDetails.getModuleName()},
                                                       myImportDetails.getBaseImportDirectory(),
                                                       true, makeNewFilesReadOnly,
                                                       project == null ? null : VcsConfiguration.getInstance(project).getCheckoutOption());
      }

      @Override
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
      }
    };
  }
}
