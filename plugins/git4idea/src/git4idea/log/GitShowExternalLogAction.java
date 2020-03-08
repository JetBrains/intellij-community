// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitExecutableManager;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryImpl;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

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

    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);

    if (project.isDefault() || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss() ||
        window == null) {
      ProgressManager.getInstance().run(new ShowLogInDialogTask(project, roots, vcs));
      return;
    }

    final Runnable showContent = () -> {
      ContentManager cm = window.getContentManager();
      if (checkIfProjectLogMatches(project, vcs, cm, roots) || checkIfAlreadyOpened(cm, roots)) {
        return;
      }

      String tabName = calcTabName(cm, roots);
      MyContentComponent component = createManagerAndContent(project, vcs, roots, true);
      Content content = ContentFactory.SERVICE.getInstance().createContent(component, tabName, false);
      content.setDisposer(component.myDisposable);
      content.setDescription(GitBundle.message("git.log.external.tab.description",
                                               StringUtil.join(roots, VirtualFile::getPath, "\n")));
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
                                                            boolean isToolWindowTab) {
    Disposable disposable = Disposer.newDisposable();
    final GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    for (VirtualFile root : roots) {
      repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, project, disposable, true));
    }
    VcsLogManager manager = new VcsLogManager(project, ServiceManager.getService(GitExternalLogTabsProperties.class),
                                              ContainerUtil.map(roots, root -> new VcsRoot(vcs, root)));
    Disposer.register(disposable, () -> manager.dispose(() -> {
      for (VirtualFile root : roots) {
        repositoryManager.removeExternalRepository(root);
      }
    }));
    MainVcsLogUi ui = manager.createLogUi(calcLogId(roots), isToolWindowTab ? VcsLogManager.LogWindowKind.TOOL_WINDOW :
                                                            VcsLogManager.LogWindowKind.STANDALONE, true);
    Disposer.register(disposable, ui);
    return new MyContentComponent(new VcsLogPanel(manager, ui), roots, disposable);
  }

  @NotNull
  private static String calcLogId(@NotNull List<? extends VirtualFile> roots) {
    return EXTERNAL + " " + StringUtil.join(roots, VirtualFile::getPath, File.pathSeparator);
  }

  @NotNull
  private static String calcTabName(@NotNull ContentManager cm, @NotNull List<? extends VirtualFile> roots) {
    String name = VcsLogBundle.message("vcs.log.tab.name") + " (" + roots.get(0).getName();
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

    List<VirtualFile> correctRoots = new ArrayList<>();
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
    List<VirtualFile> projectRoots = Arrays.asList(ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs));
    if (projectRoots.containsAll(requestedRoots)) {
      if (requestedRoots.containsAll(projectRoots)) {
        return VcsLogContentUtil.selectMainLog(cm);
      } else {
        VcsLogFilterCollection filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromRoots(requestedRoots));
        return VcsProjectLog.getInstance(project).openLogTab(filters) != null;
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

    private ShowLogInDialogTask(@NotNull Project project, @NotNull List<VirtualFile> roots, @NotNull GitVcs vcs) {
      super(project, GitBundle.message("git.log.external.loading.process"), true);
      myProject = project;
      myRoots = roots;
      myVcs = vcs;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (!GitExecutableManager.getInstance().testGitExecutableVersionValid(myProject)) {
        throw new ProcessCanceledException();
      }
    }

    @Override
    public void onSuccess() {
      if (!myProject.isDisposed()) {
        MyContentComponent content = createManagerAndContent(myProject, myVcs, myRoots, false);
        WindowWrapper window = new WindowWrapperBuilder(WindowWrapper.Mode.FRAME, content)
          .setProject(myProject)
          .setTitle(GitBundle.message("git.log.external.window.title"))
          .setPreferredFocusedComponent(content)
          .setDimensionServiceKey(GitShowExternalLogAction.class.getName())
          .build();
        Disposer.register(window, content.myDisposable);
        window.show();
      }
    }
  }
}
