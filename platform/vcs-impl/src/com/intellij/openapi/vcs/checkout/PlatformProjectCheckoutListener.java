// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;

import java.io.File;

/**
 * Open directory.
 */
public class PlatformProjectCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(final Project project, final File directory) {
    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
    if (dir != null) {
      PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
      return true;
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}
