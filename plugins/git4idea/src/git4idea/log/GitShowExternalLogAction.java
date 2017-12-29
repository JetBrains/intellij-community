/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogTabsProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVersion;
import git4idea.repo.GitRepositoryImpl;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GitShowExternalLogAction extends DumbAwareAction {
  private static final String EXTERNAL = "EXTERNAL";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final GitVcs vcs = GitVcs.getInstance(project);
    final List<VirtualFile> roots = getGitRootsFromUser(project);
    if (roots.isEmpty()) {
      return;
    }

    if (project.isDefault() || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      ProgressManager.getInstance().run(new ShowLogInDialogTask(project, roots, vcs));
      return;
    }

    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    final Runnable showContent = () -> {
      ContentManager cm = window.getContentManager();
      if (checkIfProjectLogMatches(project, vcs, cm, roots) || checkIfAlreadyOpened(cm, roots)) {
        return;
      }

      String tabName = calcTabName(cm, roots);
      MyContentComponent component = createManagerAndContent(project, vcs, roots, tabName);
      Content content = ContentFactory.SERVICE.getInstance().createContent(component, tabName, false);
      content.setDisposer(component.myDisposable);
      content.setDescription("Log for " + StringUtil.join(roots, VirtualFile::getPath, "\n"));
      content.setCloseable(true);
      cm.addContent(content);
      cm.setSelectedContent(content);
    };

    if (!window.isVisible()) {
      window.activate(showContent, true);
    }
    else {
      showContent.run();
    }
  }

  @NotNull
  private static MyContentComponent createManagerAndContent(@NotNull Project project,
                                                            @NotNull final GitVcs vcs,
                                                            @NotNull final List<VirtualFile> roots,
                                                            @Nullable String tabName) {
    final GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    for (VirtualFile root : roots) {
      repositoryManager.addExternalRepository(root, GitRepositoryImpl.getInstance(root, project, true));
    }
    VcsLogManager manager = new VcsLogManager(project, ServiceManager.getService(project, VcsLogTabsProperties.class),
                                              ContainerUtil.map(roots, root -> new VcsRoot(vcs, root)));
    Disposable disposable = () -> manager.dispose(() -> {
      for (VirtualFile root : roots) {
        repositoryManager.removeExternalRepository(root);
      }
    });
    AbstractVcsLogUi ui = manager.createLogUi(calcLogId(roots), tabName);
    Disposer.register(disposable, ui);
    return new MyContentComponent(new VcsLogPanel(manager, ui), roots, disposable);
  }

  @NotNull
  private static String calcLogId(@NotNull List<VirtualFile> roots) {
    return EXTERNAL + " " + StringUtil.join(roots, VirtualFile::getPath, File.pathSeparator);
  }

  @NotNull
  private static String calcTabName(@NotNull ContentManager cm, @NotNull List<VirtualFile> roots) {
    String name = VcsLogContentProvider.TAB_NAME + " (" + roots.get(0).getName();
    if (roots.size() > 1) {
      name += "+";
    }
    name += ")";

    String candidate = name;
    int cnt = 1;
    while (hasContentsWithName(cm, candidate)) {
      candidate = name + "-" + cnt;
      cnt++;
    }
    return candidate;
  }

  private static boolean hasContentsWithName(@NotNull ContentManager cm, @NotNull final String candidate) {
    return ContainerUtil.exists(cm.getContents(), content -> content.getDisplayName().equals(candidate));
  }

  @NotNull
  private static List<VirtualFile> getGitRootsFromUser(@NotNull Project project) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, true, false, true);
    VirtualFile[] virtualFiles = FileChooser.chooseFiles(descriptor, project, null);
    if (virtualFiles.length == 0) {
      return Collections.emptyList();
    }

    List<VirtualFile> correctRoots = ContainerUtil.newArrayList();
    for (VirtualFile vf : virtualFiles) {
      if (GitUtil.isGitRoot(new File(vf.getPath()))) {
        correctRoots.add(vf);
      }
    }
    return correctRoots;
  }

  private static boolean checkIfProjectLogMatches(@NotNull Project project,
                                                  @NotNull GitVcs vcs,
                                                  @NotNull ContentManager cm,
                                                  @NotNull List<VirtualFile> requestedRoots) {
    VirtualFile[] projectRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    if (Comparing.haveEqualElements(requestedRoots, Arrays.asList(projectRoots))) {
      Content[] contents = cm.getContents();
      for (Content content : contents) {
        if (VcsLogContentProvider.TAB_NAME.equals(content.getDisplayName())) {
          cm.setSelectedContent(content);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean checkIfAlreadyOpened(@NotNull ContentManager cm, @NotNull Collection<VirtualFile> roots) {
    for (Content content : cm.getContents()) {
      final JComponent component = content.getComponent();
      if (component instanceof MyContentComponent) {
        if (Comparing.haveEqualElements(roots, ((MyContentComponent)component).myRoots)) {
          cm.setSelectedContent(content);
          return true;
        }
      }
    }
    return false;
  }

  private static class MyContentComponent extends JPanel {
    @NotNull private final Collection<VirtualFile> myRoots;
    @NotNull private final Disposable myDisposable;

    MyContentComponent(@NotNull JComponent actualComponent, @NotNull Collection<VirtualFile> roots, @NotNull Disposable disposable) {
      super(new BorderLayout());
      myDisposable = disposable;
      myRoots = roots;
      add(actualComponent);
    }
  }

  private static class ShowLogInDialogTask extends Task.Backgroundable {
    @NotNull private final Project myProject;
    @NotNull private final List<VirtualFile> myRoots;
    @NotNull private final GitVcs myVcs;
    private GitVersion myVersion;

    private ShowLogInDialogTask(@NotNull Project project, @NotNull List<VirtualFile> roots, @NotNull GitVcs vcs) {
      super(project, "Loading Git Log...", true);
      myProject = project;
      myRoots = roots;
      myVcs = vcs;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myVersion = myVcs.getVersion();
      if (myVersion.isNull()) {
        myVcs.checkVersion();
        myVersion = myVcs.getVersion();
      }
    }

    @Override
    public void onSuccess() {
      if (!myVersion.isNull() && !myProject.isDisposed()) {
        MyContentComponent content = createManagerAndContent(myProject, myVcs, myRoots, null);
        WindowWrapper window = new WindowWrapperBuilder(WindowWrapper.Mode.FRAME, content)
          .setProject(myProject)
          .setTitle("Git Log")
          .setPreferredFocusedComponent(content)
          .setDimensionServiceKey(GitShowExternalLogAction.class.getName())
          .build();
        Disposer.register(window, content.myDisposable);
        window.show();
      }
    }
  }
}
