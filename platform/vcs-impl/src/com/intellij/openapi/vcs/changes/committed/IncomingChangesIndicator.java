package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.Disposable;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesIndicator implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.IncomingChangesIndicator");

  private final Project myProject;
  private final CommittedChangesCache myCache;
  private StatusBar myStatusBar;
  private IndicatorComponent myIndicatorComponent;

  public IncomingChangesIndicator(Project project, CommittedChangesCache cache, MessageBus bus) {
    myProject = project;
    myCache = cache;
    bus.connect().subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
      public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            refreshIndicator();
          }
        });
      }
    });
  }

  public void projectOpened() {
    myStatusBar = WindowManager.getInstance().getStatusBar(myProject);
    myIndicatorComponent = new IndicatorComponent();
    myStatusBar.addCustomIndicationComponent(myIndicatorComponent);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        myStatusBar.removeCustomIndicationComponent(myIndicatorComponent);
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "IncomingChangesIndicator";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void refreshIndicator() {
    final List<CommittedChangeList> list = myCache.getCachedIncomingChanges();
    if (list == null || list.size() == 0) {
      debug("Refreshing indicator: no changes");
      myIndicatorComponent.clear();
      myIndicatorComponent.setToolTipText("");
    }
    else {
      debug("Refreshing indicator: " + list.size() + " changes");
      myIndicatorComponent.showIcon();
      myIndicatorComponent.setToolTipText(VcsBundle.message("incoming.changes.indicator.tooltip", list.size()));
    }
    myIndicatorComponent.repaint();
  }

  private static void debug(@NonNls final String message) {
    LOG.debug(message);
  }

  private class IndicatorComponent extends SimpleColoredComponent {
    public IndicatorComponent() {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          if (!e.isPopupTrigger()) {
            ToolWindow changesView = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
            changesView.show(new Runnable() {
              public void run() {
                ChangesViewContentManager.getInstance(myProject).selectContent("Incoming");
              }
            });
          }
        }
      });
    }

    private void showIcon() {
      setIcon(IconLoader.getIcon("/actions/get.png"));
    }

    public Dimension getPreferredSize() {
      return new Dimension(18, 18);
    }
  }
}
