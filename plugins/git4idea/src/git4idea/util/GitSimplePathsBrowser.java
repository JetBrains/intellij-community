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
package git4idea.util;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.FilePathChangesTreeList;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public class GitSimplePathsBrowser extends JPanel {

  public GitSimplePathsBrowser(@NotNull Project project, @NotNull Collection<String> absolutePaths) {
    super(new BorderLayout());

    FilePathChangesTreeList browser = createBrowser(project, absolutePaths);
    ActionToolbar toolbar = createToolbar(browser);

    add(toolbar.getComponent(), BorderLayout.NORTH);
    add(browser);
  }

  @NotNull
  private static FilePathChangesTreeList createBrowser(@NotNull Project project, @NotNull Collection<String> absolutePaths) {
    List<FilePath> filePaths = toFilePaths(absolutePaths);
    FilePathChangesTreeList browser = new FilePathChangesTreeList(project, filePaths, false, false, null, null);
    browser.setChangesToDisplay(filePaths);
    return browser;
  }

  @NotNull
  private static ActionToolbar createToolbar(@NotNull FilePathChangesTreeList browser) {
    DefaultActionGroup actionGroup = new DefaultActionGroup(browser.getTreeActions());
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
  }

  @NotNull
  private static List<FilePath> toFilePaths(@NotNull Collection<String> absolutePaths) {
    return ContainerUtil.map(absolutePaths, new Function<String, FilePath>() {
      @Override
      public FilePath fun(String path) {
        return VcsUtil.getFilePath(path, false);
      }
    });
  }
}
