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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;

import java.io.File;

/**
 * @author yole
 */
public class PlatformProjectCheckoutListener implements CheckoutListener {
  public boolean processCheckedOutDirectory(final Project project, final File directory) {
    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
    if (dir != null) {
      int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.open.directory.prompt", directory.getAbsolutePath()),
                                        VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
        return true;
      }
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}
