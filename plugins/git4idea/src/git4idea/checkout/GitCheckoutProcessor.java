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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsCheckoutProcessor;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class GitCheckoutProcessor extends VcsCheckoutProcessor {

  @NotNull
  @Override
  public String getProtocol() {
    return "git";
  }

  @Override
  public void checkout(@NotNull String url,
                       @NotNull String directoryName,
                       @NotNull VirtualFile parentDirectory,
                       @NotNull CheckoutProvider.Listener listener) {
    GitCheckoutProvider.clone(ProjectManager.getInstance().getDefaultProject(), ServiceManager.getService(Git.class),
                              listener, parentDirectory, url, directoryName, parentDirectory.getPath());
  }
}
