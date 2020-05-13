// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassXmlRpcHandler;
import org.jetbrains.git4idea.ssh.GitXmlRpcHandlerService;
import org.jetbrains.git4idea.ssh.GitXmlRpcNativeSshService;

import java.io.File;
import java.io.IOException;
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
  @NotNull private final GitVersion myVersion;

  @Nullable private UUID myHttpHandler;
  private volatile boolean myHttpAuthFailed;

  @Nullable private UUID myNativeSshHandler;

  private GitHandlerAuthenticationManager(@NotNull Project project,
                                          @NotNull GitLineHandler handler,
                                          @NotNull GitVersion version) {
    myProject = project;
    myHandler = handler;
    myVersion = version;
  }

  @NotNull
  public static GitHandlerAuthenticationManager prepare(@NotNull Project project,
                                                        @NotNull GitLineHandler handler,
                                                        @NotNull GitVersion version) throws IOException {
    GitHandlerAuthenticationManager manager = new GitHandlerAuthenticationManager(project, handler, version);
    GitUtil.tryRunOrClose(manager, () -> {
      manager.prepareHttpAuth();
      manager.prepareNativeSshAuth();
      boolean useCredentialHelper = GitVcsApplicationSettings.getInstance().isUseCredentialHelper();

      boolean isConfigCommand = handler.getCommand() == GitCommand.CONFIG;
      if (isConfigCommand) return;

      boolean shouldResetCredentialHelper = !useCredentialHelper &&
                                            GitVersionSpecialty.CAN_OVERRIDE_CREDENTIAL_HELPER_WITH_EMPTY.existsIn(version);
      if (shouldResetCredentialHelper) {
        handler.overwriteConfig("credential.helper=");
      }
    });
    return manager;
  }

  @Override
  public void close() {
    cleanupHttpAuth();
    cleanupNativeSshAuth();
  }

  private void prepareHttpAuth() throws IOException {
    GitHttpAuthService service = ServiceManager.getService(GitHttpAuthService.class);
    addHandlerPathToEnvironment(GitAskPassXmlRpcHandler.GIT_ASK_PASS_ENV, service);
    GitAuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), GitPassthroughAuthenticationGate.getInstance());
    GitHttpAuthenticator httpAuthenticator = service.createAuthenticator(myProject,
                                                                         myHandler.getUrls(),
                                                                         myHandler.getWorkingDirectory(),
                                                                         authenticationGate,
                                                                         myHandler.getIgnoreAuthenticationMode());
    myHttpHandler = service.registerHandler(httpAuthenticator);
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_HANDLER_ENV, myHttpHandler.toString());
    int port = service.getXmlRcpPort();
    myHandler.addCustomEnvironmentVariable(GitAskPassXmlRpcHandler.GIT_ASK_PASS_PORT_ENV, Integer.toString(port));

    myHandler.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(@NonNls String line, Key outputType) {
        if (!httpAuthenticator.wasRequested()) {
          return;
        }

        String lowerCaseLine = StringUtil.toLowerCase(line);
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
        if (!httpAuthenticator.wasRequested()) {
          return;
        }

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

  private void prepareNativeSshAuth() throws IOException {
    GitXmlRpcNativeSshService service = ServiceManager.getService(GitXmlRpcNativeSshService.class);

    boolean doNotRememberPasswords = myHandler.getUrls().size() > 1;
    GitAuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), GitPassthroughAuthenticationGate.getInstance());
    GitNativeSshGuiAuthenticator authenticator =
      new GitNativeSshGuiAuthenticator(myProject, authenticationGate, myHandler.getIgnoreAuthenticationMode(), doNotRememberPasswords);

    myNativeSshHandler = service.registerHandler(authenticator);
    int port = service.getXmlRcpPort();

    addHandlerPathToEnvironment(GitNativeSshAskPassXmlRpcHandler.SSH_ASK_PASS_ENV, service);
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.IJ_HANDLER_ENV, myNativeSshHandler.toString());
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.IJ_PORT_ENV, Integer.toString(port));

    // SSH_ASKPASS is ignored if DISPLAY variable is not set
    String displayEnv = StringUtil.nullize(System.getenv(GitNativeSshAskPassXmlRpcHandler.DISPLAY_ENV));
    myHandler.addCustomEnvironmentVariable(GitNativeSshAskPassXmlRpcHandler.DISPLAY_ENV, StringUtil.notNullize(displayEnv, ":0.0"));

    if (Registry.is("git.use.setsid.for.native.ssh")) {
      myHandler.withNoTty();
    }
  }

  private void addHandlerPathToEnvironment(@NotNull String env,
                                           @NotNull GitXmlRpcHandlerService service) throws IOException {
    boolean useBatchFile = SystemInfo.isWindows &&
                           (!Registry.is("git.use.shell.script.on.windows") ||
                            !GitVersionSpecialty.CAN_USE_SHELL_HELPER_SCRIPT_ON_WINDOWS.existsIn(myVersion));
    File scriptFile = service.getScriptPath(useBatchFile);
    myHandler.addCustomEnvironmentVariable(env, scriptFile.getPath());
  }

  private void cleanupNativeSshAuth() {
    if (myNativeSshHandler != null) {
      ServiceManager.getService(GitXmlRpcNativeSshService.class).unregisterHandler(myNativeSshHandler);
      myNativeSshHandler = null;
    }
  }
}
