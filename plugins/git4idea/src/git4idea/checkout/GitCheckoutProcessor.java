// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkout;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.VcsCheckoutProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class GitCheckoutProcessor extends VcsCheckoutProcessor {

  @Override
  public @NotNull String getId() {
    return "git";
  }

  @Override
  public boolean checkout(final @NotNull Map<String, String> parameters,
                          final @NotNull VirtualFile parentDirectory, @NotNull String directoryName) {

    ProgressManager.getInstance().getProgressIndicator().setText(DvcsBundle.message("cloning.repository", parameters));
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    Project project = frame == null || frame.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : frame.getProject();
    return GitCheckoutProvider.doClone(project,
                                       Git.getInstance(),
                                       directoryName, parentDirectory.getPath(), parameters.get("url"));
  }
}
