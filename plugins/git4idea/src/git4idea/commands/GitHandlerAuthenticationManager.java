// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IdeaWideProxySelector;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitXmlRpcSshService;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * Manager for Git remotes authentication.
 * Provides necessary handlers and watcher for http auth failure.
 */
class GitHandlerAuthenticationManager {
  private static final Logger LOG = Logger.getInstance(GitHandlerAuthenticationManager.class);

  @NotNull private final GitLineHandler myHandler;
  @NotNull private final Project myProject;

  @Nullable private UUID myHttpHandler;
  private volatile boolean myHttpAuthFailed;

  @Nullable private UUID mySshHandler;

  private GitHandlerAuthenticationManager(@NotNull Project project, @NotNull GitLineHandler handler) {
    myProject = project;
    myHandler = handler;
  }

  @NotNull
  public static GitHandlerAuthenticationManager prepare(@NotNull Project project, @NotNull GitLineHandler handler) throws IOException {
    GitHandlerAuthenticationManager manager = new GitHandlerAuthenticationManager(project, handler);
    manager.prepareHttpAuth();
    if (GitVcsSettings.getInstance(project).isIdeaSsh()) manager.prepareSshAuth();
    return manager;
  }

  public void cleanup() {
    cleanupHttpAuth();
    cleanupSshAuth();
  }

  private void prepareHttpAuth() throws IOException {
    GitHttpAuthService service = ServiceManager.getService(GitHttpAuthService.class);
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_ENV, service.getScriptPath().getPath());
    GitHttpAuthenticator httpAuthenticator =
      service.createAuthenticator(myProject, myHandler.getCommand(), myHandler.getUrls());
    myHttpHandler = service.registerHandler(httpAuthenticator, myProject);
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_HANDLER_ENV, myHttpHandler.toString());
    int port = service.getXmlRcpPort();
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_PORT_ENV, Integer.toString(port));
    LOG.debug(String.format("myHandler=%s, port=%s", myHttpHandler, port));

    myHandler.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(@NonNls String line, Key outputType) {
        String lowerCaseLine = line.toLowerCase();
        if (lowerCaseLine.contains("authentication failed") || lowerCaseLine.contains("403 forbidden")) {
          LOG.debug("auth listener: auth failure detected: " + line);
          myHttpAuthFailed = true;
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        LOG.debug("auth listener: process terminated. auth failed=" + myHttpAuthFailed
                             + ", cancelled=" + httpAuthenticator.wasCancelled());
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
    mySshHandler = ssh.registerHandler(new GitSSHGUIHandler(myProject), myProject);
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

  private static boolean isSshUrlExcluded(@NotNull HttpConfigurable httpConfigurable, @NotNull Collection<String> urls) {
    return ContainerUtil.exists(urls, url -> {
      String host = URLUtil.parseHostFromSshUrl(url);
      return ((IdeaWideProxySelector)httpConfigurable.getOnlyBySettingsSelector()).isProxyException(host);
    });
  }
}
