// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;

import java.io.File;
import java.util.EnumSet;

/**
 * @author irengrig
 * @since 5/27/11
 */
public class ProjectDirCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File ioDotIdea = new File(directory, Project.DIRECTORY_STORE_FOLDER);

    VirtualFile dotIdea = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioDotIdea);
    // todo Rider project layout - several.idea.solution-name names
    if (dotIdea == null) return false;

    
    String message = VcsBundle.message("checkout.open.project.dir.prompt",
                                       ProjectCheckoutListener.getProductNameWithArticle(), directory.getPath());
    if (Messages.showYesNoDialog(project, message, VcsBundle.message("checkout.title"), Messages.getQuestionIcon()) == Messages.YES) {
      PlatformProjectOpenProcessor.doOpenProject(dotIdea.getParent(), project, -1, null, 
                                                 EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class));
      return true;
    }
    
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}
