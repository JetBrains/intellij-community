package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author michael.golubev
 */
public class ServersToolWindow {
  public static final String ID = "Application Servers";
  private final Project myProject;
  private final ToolWindow myToolWindow;

  public ServersToolWindow(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    myProject = project;
    myToolWindow = toolWindow;

    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      contributor.setupAvailabilityListener(project, new Runnable() {
        @Override
        public void run() {
          updateWindowAvailable(true);
        }
      });
    }
    myProject.getMessageBus().connect().subscribe(RemoteServerListener.TOPIC, new RemoteServerListener() {
      @Override
      public void serverAdded(@NotNull RemoteServer<?> server) {
        updateWindowAvailable(true);
      }

      @Override
      public void serverRemoved(@NotNull RemoteServer<?> server) {
        updateWindowAvailable(false);
      }
    });

    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final ServersToolWindowContent serversContent = new ServersToolWindowContent(project);
    Content content = contentFactory.createContent(serversContent.getMainPanel(), null, false);
    Disposer.register(content, serversContent);
    myToolWindow.getContentManager().addContent(content);

    updateWindowAvailable(false);
  }

  private void updateWindowAvailable(boolean showIfAvailable) {
    boolean available = isAvailable();
    boolean doShow = !myToolWindow.isAvailable() && available;
    if (myToolWindow.isAvailable() && !available) {
      myToolWindow.hide(null);
    }
    myToolWindow.setAvailable(available, null);
    if (showIfAvailable && doShow) {
      myToolWindow.show(null);
    }
  }

  private boolean isAvailable() {
    if (!RemoteServersManager.getInstance().getServers().isEmpty()) {
      return true;
    }
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      if (contributor.canContribute(myProject)) {
        return true;
      }
    }
    return false;
  }
}
