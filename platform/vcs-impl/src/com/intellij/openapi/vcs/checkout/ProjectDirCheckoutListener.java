// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.platform.PlatformProjectOpenProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

final class ProjectDirCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    Path dotIdea = directory.toPath().resolve(Project.DIRECTORY_STORE_FOLDER);
    // todo Rider project layout - several.idea.solution-name names
    if (!Files.exists(dotIdea)) {
      return false;
    }

    String message = VcsBundle.message("checkout.open.project.dir.prompt",
                                       ProjectCheckoutListener.getProductNameWithArticle(), directory.getPath());
    if (Messages.showYesNoDialog(project, message, VcsBundle.message("checkout.title"), Messages.getQuestionIcon()) == Messages.YES) {
      PlatformProjectOpenProcessor.doOpenProject(dotIdea.getParent(), new OpenProjectTask(false, project), -1);
      return true;
    }

    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}
