package org.jetbrains.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.util.Consumer;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.WebServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

class WebServerManagerImpl extends WebServerManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(WebServerManager.class);

  @NonNls
  private static final String PROPERTY_RPC_PORT = "rpc.port";
  private static final int FIRST_PORT_NUMBER = 63342;
  private static final int PORTS_COUNT = 20;

  private volatile int detectedPortNumber = -1;
  private final AtomicBoolean started = new AtomicBoolean(false);

  @Nullable
  private WebServer server;

  public int getPort() {
    return detectedPortNumber == -1 ? getDefaultPort() : detectedPortNumber;
  }

  public WebServerManager waitForStart() {
    Future<?> serverStartFuture = startServerInPooledThread();
    if (serverStartFuture != null) {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
      try {
        serverStartFuture.get();
      }
      catch (InterruptedException ignored) {
      }
      catch (ExecutionException ignored) {
      }
    }
    return this;
  }

  public void addClosingListener(ChannelFutureListener listener) {
    if (server != null) {
      server.addClosingListener(listener);
    }
  }

  private static int getDefaultPort() {
    return System.getProperty(PROPERTY_RPC_PORT) == null ? FIRST_PORT_NUMBER : Integer.parseInt(System.getProperty(PROPERTY_RPC_PORT));
  }

  static final class MyPostStartupActivity implements StartupActivity, DumbAware {
    private boolean veryFirstProjectOpening = true;

    @Override
    public void runActivity(Project project) {
      if (!veryFirstProjectOpening) {
        return;
      }

      veryFirstProjectOpening = false;
      WebServerManager webServerManager = WebServerManager.getInstance();
      if (webServerManager instanceof WebServerManagerImpl) {
        ((WebServerManagerImpl)webServerManager).startServerInPooledThread();
      }
    }
  }

  private Future<?> startServerInPooledThread() {
    if (!started.compareAndSet(false, true)) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return null;
    }

    return application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          server = new WebServer();
        }
        catch (ChannelException e) {
          LOG.info(e);
          String groupDisplayId = "Web Server";
          Notifications.Bus.register(groupDisplayId, NotificationDisplayType.STICKY_BALLOON);
          new Notification(groupDisplayId, "Internal HTTP server disabled",
                           "Cannot start internal HTTP server. Git integration, JavaScript debugger and LiveEdit may operate with errors. " +
                           "Please check your firewall settings and restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                           NotificationType.ERROR).notify(null);
          return;
        }

        detectedPortNumber = server.start(getDefaultPort(), PORTS_COUNT, true, new Computable<Consumer<ChannelPipeline>[]>() {
          @Override
          public Consumer<ChannelPipeline>[] compute() {
            Consumer<ChannelPipeline>[] consumers = Extensions.getExtensions(EP_NAME);
            if (consumers.length == 0) {
              LOG.warn("web server will be stopped, there are no pipeline consumers");
              SimpleTimer.getInstance().setUp(server.createShutdownTask(), 3000);
            }
            return consumers;
          }
        });

        if (detectedPortNumber == -1) {
          LOG.info("web server cannot be started, cannot bind to port");
        }
        else {
          LOG.info("web server started, port " + detectedPortNumber);
        }
      }
    });
  }

  @Override
  public void dispose() {
    if (started.get() && server != null) {
      server.stop();
      LOG.info("web server stopped");
    }
  }
}