// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public class GitSimplePathsBrowser extends JPanel {

  private final ChangesTree browser;

  public GitSimplePathsBrowser(@NotNull Project project, @NotNull Collection<String> absolutePaths) {
    super(new BorderLayout());

    browser = createBrowser(project, absolutePaths);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("GitPathBrowser", group, true);
    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(toolbar, browser);

    add(toolbarPanel, BorderLayout.NORTH);
    add(ScrollPaneFactory.createScrollPane(browser));
  }

  public void setEmptyText(@NotNull @NlsContexts.StatusText String text) {
    browser.setEmptyText(text);
  }

  private static @NotNull ChangesTree createBrowser(@NotNull Project project, @NotNull Collection<String> absolutePaths) {
    List<FilePath> filePaths = toFilePaths(absolutePaths);
    return new AsyncChangesTreeImpl.FilePaths(project, false, false, filePaths);
  }

  private static @NotNull List<FilePath> toFilePaths(@NotNull Collection<String> absolutePaths) {
    return ContainerUtil.map(absolutePaths, path -> VcsUtil.getFilePath(path, false));
  }
}
