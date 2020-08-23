// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Consumer;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * Status bar widget which displays the current branch for the file currently open in the editor.
 */
public class GitBranchWidget extends DvcsStatusWidget<GitRepository> {
  private static final Icon INCOMING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingLayer);
  private static final Icon INCOMING_OUTGOING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingOutgoingLayer);
  private static final Icon OUTGOING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.OutgoingLayer);
  private static final @NonNls String ID = "git";
  private final GitVcsSettings mySettings;
  private static final boolean showNewNavbarVCSGroup = UISettings.getInstance().getShowNewNavbarVCSGroup();

  public GitBranchWidget(@NotNull Project project) {
    super(project, GitVcs.NAME);
    mySettings = GitVcsSettings.getInstance(project);

    project.getMessageBus().connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, r -> updateLater());
    project.getMessageBus().connect(this).subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, this::updateLater);
  }

  @Override
  public @NotNull String ID() {
    return ID;
  }

  @Override
  public StatusBarWidget copy() {
    return new GitBranchWidget(getProject());
  }

  @Nullable
  @Override
  @CalledInAwt
  protected GitRepository guessCurrentRepository(@NotNull Project project) {
    return DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
  }

  @Nullable
  @Override
  protected Icon getIcon(@NotNull GitRepository repository) {
    String currentBranchName = repository.getCurrentBranchName();
    if (repository.getState() == Repository.State.NORMAL && currentBranchName != null) {
      GitRepository indicatorRepo =
        (GitRepositoryManager.getInstance(myProject).moreThanOneRoot() && mySettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC)
        ? repository
        : null;
      boolean hasIncoming = GitBranchIncomingOutgoingManager.getInstance(myProject).hasIncomingFor(indicatorRepo, currentBranchName);
      boolean hasOutgoing = GitBranchIncomingOutgoingManager.getInstance(myProject).hasOutgoingFor(indicatorRepo, currentBranchName);
      if (hasIncoming) {
        return hasOutgoing ? INCOMING_OUTGOING_LAYERED : INCOMING_LAYERED;
      }
      else if (hasOutgoing) return OUTGOING_LAYERED;
    }
    return super.getIcon(repository);
  }

  @NotNull
  @Override
  protected String getFullBranchName(@NotNull GitRepository repository) {
    return GitBranchUtil.getDisplayableBranchText(repository);
  }

  @Override
  protected boolean isMultiRoot(@NotNull Project project) {
    return !GitUtil.justOneGitRepository(project);
  }

  @NotNull
  @Override
  protected ListPopup getPopup(@NotNull Project project, @NotNull GitRepository repository) {
    return GitBranchPopup.getInstance(project, repository).asListPopup();
  }

  @Override
  protected void rememberRecentRoot(@NotNull String path) {
    mySettings.setRecentRoot(path);
  }

  public static class Listener implements VcsRepositoryMappingListener {
    private final Project myProject;

    public Listener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void mappingChanged() {
      myProject.getService(StatusBarWidgetsManager.class).updateWidget(Factory.class);
    }
  }

  public static class Factory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
      return GitBundle.message("git.status.bar.widget.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
      return !Registry.is("vcs.new.widget") && !GitRepositoryManager.getInstance(project).getRepositories().isEmpty();
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      if(showNewNavbarVCSGroup){
        return createMockVCSWidget();
      }
      return new GitBranchWidget(project);
    }

    private StatusBarWidget createMockVCSWidget() {
      return new StatusBarWidget(){

        @Override
        public @Nullable WidgetPresentation getPresentation() {
          return new StatusBarWidget.TextPresentation(){
            @Override
            public @NotNull @NlsContexts.Label String getText() {
              return "";
            }
            @Override
            public float getAlignment() {
              return 0;
            }
            @Override
            public @Nullable @NlsContexts.Tooltip String getTooltipText() {
              return null;
            }
            @Override
            public @Nullable Consumer<MouseEvent> getClickConsumer() {
              return null;
            }
          };
        }

        @Override
        public void dispose() {
        }

        @Override
        public @NonNls @NotNull String ID() {
          return "";
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
        }
      };
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
      Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return true;
    }
  }
}
