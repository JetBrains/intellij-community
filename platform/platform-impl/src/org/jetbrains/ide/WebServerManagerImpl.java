package org.jetbrains.ide;

import com.intellij.ide.browsers.Url;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.NetUtils;
import org.jboss.netty.channel.ChannelException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.WebServer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebServerManagerImpl extends WebServerManager {
  private static final Logger LOG = Logger.getInstance(WebServerManager.class);
  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull
    @Override
    protected NotificationGroup compute() {
      return new NotificationGroup("Built-in Server", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };


  @NonNls
  public static final String PROPERTY_RPC_PORT = "rpc.port";
  private static final int FIRST_PORT_NUMBER = 63342;
  private static final int PORTS_COUNT = 20;

  private volatile int detectedPortNumber = -1;
  private final AtomicBoolean started = new AtomicBoolean(false);

  @Nullable
  private WebServer server;
  private boolean myEnabledInUnitTestMode;

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
    if (application.isUnitTestMode() && !myEnabledInUnitTestMode) {
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

        Disposer.register(ApplicationManager.getApplication(), server);
        detectedPortNumber = server.start(getDefaultPort(), PORTS_COUNT, true);
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
  @Nullable
  public Disposable getServerDisposable() {
    return server;
  }

  @Override
  public boolean isOnBuiltInWebServer(@Nullable Url url) {
    return url != null && !StringUtil.isEmpty(url.getAuthority()) && isOnBuiltInWebServerByAuthority(url.getAuthority());
  }

  private static boolean isOnBuiltInWebServerByAuthority(@NotNull String authority) {
    int portIndex = authority.indexOf(':');
    if (portIndex < 0 || portIndex == authority.length() - 1) {
      return false;
    }

    int port = StringUtil.parseInt(authority.substring(portIndex + 1), -1);
    if (port == -1) {
      return false;
    }

    //BuiltInServerOptions options = BuiltInServerOptions.getInstance();
    //int idePort = BuiltInServerManager.getInstance().getPort();
    //if (options.builtInServerPort != port && idePort != port) {
    //  return false;
    //}

    String host = authority.substring(0, portIndex);
    if (NetUtils.isLocalhost(host)) {
      return true;
    }

    try {
      InetAddress inetAddress = InetAddress.getByName(host);
      return inetAddress.isLoopbackAddress() ||
             inetAddress.isAnyLocalAddress() /*||
             (options.builtInServerAvailableExternally && idePort != port && NetworkInterface.getByInetAddress(inetAddress) != null)*/;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Pass true to start the WebServerManager even in the {@link Application#isUnitTestMode() unit test mode}.
   */
  @TestOnly
  public void setEnabledInUnitTestMode(boolean enabled) {
    myEnabledInUnitTestMode = enabled;
  }

}