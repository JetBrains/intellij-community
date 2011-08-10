/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.GitRebase;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubRebaseDialog;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubRebase extends GitRebase {
  private String myOriginName;

  /**
   * {@inheritDoc}
   */
  @Nullable
  protected GitLineHandler createHandler(final Project project, final List<VirtualFile> gitRoots, final VirtualFile defaultRoot) {
    final GithubRebaseDialog dialog = new GithubRebaseDialog(project, gitRoots, defaultRoot);
    dialog.configure(myOriginName);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.handler();
  }

  public void setRebaseOrigin(final String originName) {
    myOriginName = originName;
  }
}

