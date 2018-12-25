// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.HttpConfigurable;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassXmlRpcHandler;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitXmlRpcNativeSshService;
import org.jetbrains.git4idea.ssh.GitXmlRpcSshService;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * Manager for Git remotes authentication.
 * Provides necessary handlers and watcher for http auth failure.
 */
public class GitHandlerAuthenticationManager implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(GitHandlerAuthenticationManager.class);

  @NotNull private final GitLineHandler myHandler;
  @NotNull private final Project myProject;

  @Nullable private UUID myHttpHandler;
  private volatile boolean myHttpAuthFailed;

  @Nullable private UUID mySshHandler;

  @Nullable private UUID myNativeSshHandler;

  private GitHandlerAuthenticationManager(@NotNull Project project, @NotNull GitLineHandler handler) {
    myProject = project;
    myHandler = handler;
  }

  @NotNull
  public static GitHandlerAuthenticationManager prepare(@NotNull Project project, @NotNull GitLineHandler handler) throws IOException {
    GitHandlerAuthenticationManager manager = new GitHandlerAuthenticationManager(project, handler);
    manager.prepareHttpAuth();
    if (GitVcsApplicationSettings.getInstance().isUseIdeaSsh()) {
      manager.prepareSshAuth();
    }
    else if (Registry.is("git.ssh.native.override.ssh.askpass")) {
      manager.prepareNativeSshAuth();
    }
    return manager;
  }

  @Override
  public void close() {
    cleanupHttpAuth();
    cleanupSshAuth();
    cleanupNativeSshAuth();
  }

  private void prepareHttpAuth() throws IOException {
    GitHttpAuthService service = ServiceManager.getService(GitHttpAuthService.class);
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_ENV, service.getScriptPath().getPath());
    GitAuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), GitPassthroughAuthenticationGate.getInstance());
    GitHttpAuthenticator httpAuthenticator = service.createAuthenticator(myProject,
                                                                         myHandler.getUrls(),
                                                                         authenticationGate,
                                                                         myHandler.getIgnoreAuthenticationMode());
    myHttpHandler = service.registerHandler(httpAuthenticator, myProject);
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_HANDLER_ENV, myHttpHandler.toString());
    int port = service.getXmlRcpPort();
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_PORT_ENV, Integer.toString(port));
    LOG.debug(String.format("myHandler=%s, port=%s", myHttpHandler, port));

    myHandler.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(@NonNls String line, Key outputType) {
        String lowerCaseLine = line.toLowerCase();
        if (lowerCaseLine.contains("authentication failed") ||
            lowerCaseLine.contains("403 forbidden") ||
            lowerCaseLine.contains("error: 400") ||
            (lowerCaseLine.contains("fatal: repository") && lowerCaseLine.contains("not found")) ||
            (lowerCaseLine.contains("fatal: unable to access") && lowerCaseLine.contains("the requested url returned error: 403")) ||
            lowerCaseLine.contains("[remote rejected] (permission denied)")) {
          LOG.debug("auth listener: auth failure detected: " + line);
          myHttpAuthFailed = true;
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        LOG.debug("auth listener: process terminated. auth failed=" + myHttpAuthFailed + ", cancelled=" + httpAuthenticator.wasCancelled());
        if (!httpAuthenticator.wasCancelled()) {
          if (myHttpAuthFailed) {
            httpAuthenticator.forgetPassword();
          }
          else {
            httpAuthenticator.saveAuthData();
          }
        }
        else {
          myHttpAuthFailed = false;
        }
      }
    });
  }

  private void cleanupHttpAuth() {
    if (myHttpHandler != null) {
      ServiceManager.getService(GitHttpAuthService.class).unregisterHandler(myHttpHandler);
      myHttpHandler = null;
    }
  }

  public boolean isHttpAuthFailed() {
    return myHttpAuthFailed;
  }

  private void prepareSshAuth() throws IOException {
    GitXmlRpcSshService ssh = ServiceManager.getService(GitXmlRpcSshService.class);
    myHandler.addCustomEnvironmentVariable(GitSSHHandler.GIT_SSH_ENV, ssh.getScriptPath().getPath());
    myHandler.addCustomEnvironmentVariable(GitSSHHandler.GIT_SSH_VAR, "ssh");
    GitAuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), GitPassthroughAuthenticationGate.getInstance());
    GitSSHGUIHandler guiHandler = new GitSSHGUIHandler(myProject, authenticationGate, myHandler.getIgnoreAuthenticationMode());
    mySshHandler = ssh.registerHandler(guiHandler, myProject);
    myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_HANDLER_ENV, mySshHandler.toString());
    int port = ssh.getXmlRcpPort();
    myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PORT_ENV, Integer.toString(port));
    LOG.debug(String.format("myHandler=%s, port=%s", mySshHandler, port));

    final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    boolean useHttpProxy =
      httpConfigurable.USE_HTTP_PROXY && !isSshUrlExcluded(httpConfigurable, myHandler.getUrls());
    myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_USE_PROXY_ENV, String.valueOf(useHttpProxy));

    if (useHttpProxy) {
      myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PROXY_HOST_ENV, StringUtil.notNullize(httpConfigurable.PROXY_HOST));
      myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PROXY_PORT_ENV, String.valueOf(httpConfigurable.PROXY_PORT));
      boolean proxyAuthentication = httpConfigurable.PROXY_AUTHENTICATION;
      myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PROXY_AUTHENTICATION_ENV, String.valueOf(proxyAuthentication));

      if (proxyAuthentication) {
        myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PROXY_USER_ENV, StringUtil.notNullize(httpConfigurable.getProxyLogin()));
        myHandler.addCustomEnvironmentVariable(GitSSHHandler.SSH_PROXY_PASSWORD_ENV,
                                               StringUtil.notNullize(httpConfigurable.getPlainProxyPassword()));
      }
    }
  }

  private void cleanupSshAuth() {
    if (mySshHandler != null) {
      ServiceManager.getService(GitXmlRpcSshService.class).unregisterHandler(mySshHandler);
      mySshHandler = null;
    }
  }

  private void prepareNativeSshAuth() throws IOException {
    GitXmlRpcNativeSshService service = ServiceManager.getService(GitXmlRpcNativeSshService.class);

    boolean doNotRememberPasswords = myHandler.getUrls().size() > 1;
    GitAuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), GitPassthroughAuthenticationGate.getInstance());
    GitNativeSshGuiAuthenticator authenticator =
      new GitNativeSshGuiAuthenticator(myProject, authenticationGate, myHandler.getIgnoreAuthenticationMode(), doNotRememberPasswords);

    myNativeSshHandler = service.registerHandler(authenticator, myProject);
    int port = service.getXmlRcpPort();

    boolean useBatchFile = SystemInfo.isWindows &&
                           (!Registry.is("git.use.shell.script.on.windows") ||
                            !GitVersionSpecialty.CAN_USE_SHELL_HELPER_SCRIPT_ON_WINDOWS.existsIn(myProject));

    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.SSH_ASK_PASS_ENV,
                                           service.getScriptPath(useBatchFile).getPath());
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.IJ_HANDLER_ENV, myNativeSshHandler.toString());
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.IJ_PORT_ENV, Integer.toString(port));
    LOG.debug(String.format("myHandler=%s, port=%s", myNativeSshHandler, port));

    // SSH_ASKPASS is ignored if DISPLAY variable is not set
    String displayEnv = StringUtil.nullize(System.getenv(GitNativeSshAskPassXmlRpcHandler.DISPLAY_ENV));
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.DISPLAY_ENV, StringUtil.notNullize(displayEnv, ":0.0"));

    if (Registry.is("git.use.setsid.for.native.ssh")) {
      myHandler.withNoTty();
    }
  }

  private void cleanupNativeSshAuth() {
    if (myNativeSshHandler != null) {
      ServiceManager.getService(GitXmlRpcNativeSshService.class).unregisterHandler(myNativeSshHandler);
      myNativeSshHandler = null;
    }
  }

  private static boolean isSshUrlExcluded(@NotNull HttpConfigurable httpConfigurable, @NotNull Collection<String> urls) {
    return ContainerUtil.exists(urls, url -> {
      String host = URLUtil.parseHostFromSshUrl(url);
      return httpConfigurable.isProxyException(host);
    });
  }
}
