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
      if (rc == 0) {
        PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
        return true;
      }
    }
    return false;
  }
}
