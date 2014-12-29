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
package git4idea.checkout;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.components.ServiceManager;
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

  @NotNull
  @Override
  public String getId() {
    return "git";
  }

  @Override
  public boolean checkout(@NotNull final Map<String, String> parameters,
                          @NotNull final VirtualFile parentDirectory, @NotNull String directoryName) {

    ProgressManager.getInstance().getProgressIndicator().setText(DvcsBundle.message("cloning.repository", parameters));
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    Project project = frame == null || frame.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : frame.getProject();
    return GitCheckoutProvider.doClone(project,
                                       ServiceManager.getService(Git.class),
                                       directoryName, parentDirectory.getPath(), parameters.get("url"));
  }
}
