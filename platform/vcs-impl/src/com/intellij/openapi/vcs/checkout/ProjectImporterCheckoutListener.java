// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;

import java.io.File;
import java.util.Collections;

public class ProjectImporterCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    final File[] files = directory.listFiles();
    if (files != null) {
      final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      
      // need to refresh the project folder in order to get actual files via findFileByIoFile
      localFileSystem.refreshIoFiles(Collections.singleton(directory));
      
      for (File file : files) {
        if (file.isDirectory()) continue;
        final VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
        if (virtualFile != null) {
          final ProjectOpenProcessor openProcessor = ProjectOpenProcessor.getImportProvider(virtualFile);
          if (openProcessor != null) {
            int rc = Messages
              .showYesNoDialog(project, VcsBundle.message("checkout.open.project.prompt", ProjectCheckoutListener.getProductNameWithArticle(), file.getPath()),
                               VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
            if (rc == Messages.YES) {
              openProcessor.doOpenProject(virtualFile, project, false);
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}