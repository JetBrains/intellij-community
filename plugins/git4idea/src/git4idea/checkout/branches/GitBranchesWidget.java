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
package git4idea.checkout.branches;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * The git branches widget
 */
public class GitBranchesWidget extends TextPanel implements CustomStatusBarWidget {
  /**
   * The arrows icon
   */
  private static final Icon ARROWS_ICON = IconLoader.getIcon("/ide/statusbar_arrows.png");
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance(GitBranchesWidget.class.getName());

  /**
   * The ID of the widget
   */
  public static final String ID = "git4idea.BranchConfigurations";
  /**
   * The listener
   */
  final GitBranchConfigurationsListener myConfigurationsListener;
  /**
   * The project
   */
  final Project myProject;
  /**
   * The status bar
   */
  private final StatusBar myStatusBar;
  /**
   * The configurations instance
   */
  private final GitBranchConfigurations myConfigurations;
  /**
   * The selectable configurations. Null if invalidated or non-initialized
   */
  private AnAction[] mySelectableConfigurations;
  /**
   * The candidate remote configurations. Null if invalidated or non-initialized
   */
  private AnAction[] myRemoveConfigurations;
  /**
   * If true, the popup is enabled
   */
  private boolean myPopupEnabled = false;
  /**
   * The action group for pop up
   */
  private DefaultActionGroup myPopupActionGroup;
  /**
   * The current popup
   */
  private ListPopup myPopup;
  /**
   * The default foreground color
   */
  private final Color myDefaultForeground;


  /**
   * The constructor
   *
   * @param project        the project instance
   * @param statusBar      the status bar
   * @param configurations the configuration settings
   */
  public GitBranchesWidget(Project project, StatusBar statusBar, GitBranchConfigurations configurations) {
    myProject = project;
    myStatusBar = statusBar;
    setBorder(WidgetBorder.INSTANCE);
    myConfigurations = configurations;
    myDefaultForeground = getForeground();
    myConfigurationsListener = new MyGitBranchConfigurationsListener();
    myConfigurations.addConfigurationListener(myConfigurationsListener);
    Disposer.register(myConfigurations, this);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        showPopup();
      }
    });
    updateLabel();
  }

  /**
   * Create and install widget
   *
   * @param project        the context project
   * @param configurations the configurations to use
   * @return the action that uninstalls widget
   */
  static Runnable install(final Project project, final GitBranchConfigurations configurations) {
    final Ref<GitBranchesWidget> widget = new Ref<GitBranchesWidget>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          final GitBranchesWidget w = new GitBranchesWidget(project, statusBar, configurations);
          statusBar.addWidget(w, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), project);
          widget.set(w);
        }
      }
    });
    return new Runnable() {
      @Override
      public void run() {
        if (widget.get() != null) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              Disposer.dispose(widget.get());
            }
          });
        }
      }
    };

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JComponent getComponent() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void install(@NotNull StatusBar statusBar) {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void dispose() {
    myConfigurations.removeConfigurationListener(myConfigurationsListener);
    myStatusBar.removeWidget(ID());
  }

  /**
   * @return get or create remotes group
   */
  private AnAction[] getRemotes() {
    assert myPopupEnabled : "pop should be enabled";
    if (myRemoveConfigurations == null) {
      ArrayList<AnAction> rc = new ArrayList<AnAction>();
      for (final String c : myConfigurations.getRemotesCandidates()) {
        rc.add(new AnAction(c) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myConfigurations.startCheckout(null, c, false);
          }
        });
      }
      rc.add(new RefreshRemotesAction());
      myRemoveConfigurations = rc.toArray(new AnAction[rc.size()]);
    }
    return myRemoveConfigurations;
  }

  void showPopup() {
    if (!myPopupEnabled) {
      return;
    }
    final DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    final DataContext dataContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), myProject, parent);
    myPopup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, getPopupActionGroup(), dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                              new Runnable() {
                                @Override
                                public void run() {
                                  myPopup = null;
                                }
                              }, 20);
    final Dimension dimension = myPopup.getContent().getPreferredSize();
    final Point at = new Point(0, -dimension.height);
    myPopup.show(new RelativePoint(getComponent(), at));
  }

  /**
   * @return get or create selectable configuration group
   */
  private AnAction[] getSelectable() {
    assert myPopupEnabled : "pop should be enabled";
    if (mySelectableConfigurations == null) {
      ArrayList<AnAction> rc = new ArrayList<AnAction>();

      GitBranchConfiguration current;
      try {
        current = myConfigurations.getCurrentConfiguration();
      }
      catch (VcsException e) {
        LOG.error("Unexpected error at this point", e);
        mySelectableConfigurations = new AnAction[0];
        return mySelectableConfigurations;
      }
      String name = current == null ? "" : current.getName();
      for (final String c : myConfigurations.getConfigurationNames()) {
        if (name.equals(c)) {
          // skip current config
          continue;
        }
        rc.add(new AnAction(c) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            try {
              final GitBranchConfiguration toCheckout = myConfigurations.getConfiguration(c);
              if (toCheckout == null) {
                throw new VcsException("The configuration " + c + " cannot be found.");
              }
              myConfigurations.startCheckout(toCheckout, null, true);
            }
            catch (VcsException e1) {
              GitUIUtil.showOperationError(myProject, e1, "Unable to load: " + c);
            }
          }
        });
      }
      mySelectableConfigurations = rc.toArray(new AnAction[rc.size()]);
    }
    return mySelectableConfigurations;
  }

  /**
   * @return the action group for popup
   */
  ActionGroup getPopupActionGroup() {
    if (myPopupActionGroup == null) {
      myPopupActionGroup = new DefaultActionGroup(null, false);
      myPopupActionGroup.addAction(new AnAction("Manage Configurations ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          GitManageConfigurationsDialog.showDialog(myProject, myConfigurations);
        }
      });
      myPopupActionGroup.addAction(new AnAction("Modify Current Configuration ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          try {
            final GitBranchConfiguration current = myConfigurations.getCurrentConfiguration();
            myConfigurations.startCheckout(current, null, false);
          }
          catch (VcsException e1) {
            LOG.error("The current configuration must exists at this point", e1);
          }
        }
      });
      myPopupActionGroup.addAction(new AnAction("New Configuration ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myConfigurations.startCheckout(null, null, false);
        }
      });
      myPopupActionGroup.add(new MyRemotesActionGroup());
      myPopupActionGroup.addSeparator("Branch Configurations");
      myPopupActionGroup.add(new MySelectableActionGroup());
    }
    return myPopupActionGroup;
  }

  /**
   * Update label on the widget
   */
  private void updateLabel() {
    cancelPopup();
    final GitBranchConfigurations.SpecialStatus status = myConfigurations.getSpecialStatus();
    String text;
    myPopupEnabled = false;
    Color color = Color.RED;
    String tooltip;
    switch (status) {
      case CHECKOUT_IN_PROGRESS:
        text = "Checkout...";
        color = Color.BLUE;
        tooltip = "A checkout operation is in progress.";
        break;
      case MERGING:
        text = "Merging...";
        tooltip = "Merge is in progress in some vcs roots.";
        break;
      case REBASING:
        tooltip = "Rebase is in progress in some vcs roots.";
        text = "Rebasing...";
        break;
      case NON_GIT:
        tooltip = "No valid vcs roots are configuration for the project.";
        text = "Non-Git project";
        break;
      case SUBMODULES:
        tooltip =
          "<html>The submodules are unsupported in the current version.<br/>It is possible to disable widget in Git Vcs settings.</html>";
        text = "Submodules unsupported";
        break;
      case NORMAL:
        GitBranchConfiguration current;
        try {
          current = myConfigurations.getCurrentConfiguration();
        }
        catch (VcsException e) {
          current = null;
        }
        if (current == null) {
          tooltip = "The branch configurations are not yet detected.";
          text = "Detecting...";
        }
        else {
          myPopupEnabled = true;
          text = current.getName();
          tooltip = "<html>The Git branch configuration <b>" + text + "</b> is selected.<br/>Click to select other configuration.</html>";
          color = myDefaultForeground;
        }
        break;
      default:
        tooltip = "Unknown status: " + status;
        text = "Unknown status: " + status;
    }
    if (!SystemInfo.isMac) {
      setForeground(color);
    }
    setToolTipText(tooltip);
    setText(text);
    invalidate();
  }

  @Override
  protected void paintComponent(@NotNull final Graphics g) {
    super.paintComponent(g);

    if (getText() != null && myPopupEnabled) {
      final Rectangle r = getBounds();
      final Insets insets = getInsets();
      ARROWS_ICON
        .paintIcon(this, g, r.width - insets.right - ARROWS_ICON.getIconWidth() - 2, r.height / 2 - ARROWS_ICON.getIconHeight() / 2);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    return new Dimension(preferredSize.width + ARROWS_ICON.getIconWidth() + 4, preferredSize.height);
  }

  @Override
  protected String getTextForPreferredSize() {
    return getText();
  }

  /**
   * Cancel popup if it is shown
   */
  private void cancelPopup() {
    if (myPopup != null) {
      myPopup.cancel();
    }
  }

  /**
   * Remotes action group
   */
  class MySelectableActionGroup extends ActionGroup {
    /**
     * The constructor
     */
    public MySelectableActionGroup() {
      super(null, false);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return getSelectable();
    }
  }

  /**
   * Remotes action group
   */
  class MyRemotesActionGroup extends ActionGroup {
    /**
     * The constructor
     */
    public MyRemotesActionGroup() {
      super("Remotes", true);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return getRemotes();
    }
  }

  /**
   * The configuration listener
   */
  class MyGitBranchConfigurationsListener implements GitBranchConfigurationsListener {
    /**
     * {@inheritDoc}
     */
    @Override
    public void configurationsChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          mySelectableConfigurations = null;
        }
      });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void specialStatusChanged() {
      refreshLabel();
    }

    private void refreshLabel() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          updateLabel();
        }
      });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void currentConfigurationChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          mySelectableConfigurations = null;
          updateLabel();
        }
      });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void referencesChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myRemoveConfigurations = null;
          updateLabel();
        }
      });
    }
  }

  /**
   * Refresh git remotes
   */
  class RefreshRemotesAction extends AnAction {
    /**
     * The constructor
     */
    public RefreshRemotesAction() {
      super("Refresh Remotes...", "Fetch all references for git vcs roots", IconLoader.findIcon("/vcs/refresh.png"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(AnActionEvent e) {

      final String title = "Refreshing remotes";
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
          try {
            for (VirtualFile root : GitUtil.getGitRoots(myProject, GitVcs.getInstance(myProject))) {
              GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.FETCH);
              h.addParameters("--all", "-v");
              final Collection<VcsException> e = GitHandlerUtil
                .doSynchronouslyWithExceptions(h, indicator, "Fetching all for " + GitUtil.relativePath(myProject.getBaseDir(), root));
              exceptions.addAll(e);
            }
          }
          catch (VcsException e1) {
            exceptions.add(e1);
          }
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              if (!exceptions.isEmpty()) {
                GitUIUtil.showTabErrors(myProject, title, exceptions);
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.ERROR, "Refreshing remotes failed.");
              }
              else {
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO, "Refreshing remotes complete.");
              }
              myRemoveConfigurations = null;
              cancelPopup();
            }
          });
        }
      });
    }
  }
}
