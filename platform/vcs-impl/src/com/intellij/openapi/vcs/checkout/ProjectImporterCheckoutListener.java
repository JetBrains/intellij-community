// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;

import java.io.File;
import java.util.Collections;

/**
 * Open project which has {@code build.gradle}, {@link pom.xml} or other file for a build system.
 */
public class ProjectImporterCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File[] files = directory.listFiles();
    if (files != null) {
      LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      
      // need to refresh the project folder in order to get actual files via findFileByIoFile
      localFileSystem.refreshIoFiles(Collections.singleton(directory));

      for (File file : files) {
        if (file.isDirectory()) continue;
        VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
        if (virtualFile != null) {
          ProjectOpenProcessor openProcessor = ProjectOpenProcessor.getImportProvider(virtualFile);
          if (openProcessor != null) {
            openProcessor.doOpenProject(virtualFile, project, false);
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