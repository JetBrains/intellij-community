// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.externalProcessAuthHelper.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import externalApp.nativessh.NativeSshAskPassAppHandler;
import git4idea.GitUtil;
import git4idea.commit.signing.GpgAgentConfigurator;
import git4idea.commit.signing.PinentryService;
import git4idea.config.*;
import git4idea.config.gpg.GitGpgConfigUtilsKt;
import git4idea.http.GitAskPassAppHandler;
import git4idea.repo.GitProjectConfigurationCache;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * Manager for Git remotes authentication.
 * Provides necessary handlers and watcher for http auth failure.
 */
public final class GitHandlerAuthenticationManager implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(GitHandlerAuthenticationManager.class);

  private final @NotNull GitLineHandler myHandler;
  private final @NotNull Project myProject;
  private final @NotNull GitVersion myVersion;

  private @Nullable UUID myHttpHandler;
  private volatile boolean myHttpAuthFailed;

  private @Nullable UUID myNativeSshHandler;

  private final Disposable myDisposable = Disposer.newDisposable();

  private GitHandlerAuthenticationManager(@NotNull Project project,
                                          @NotNull GitLineHandler handler,
                                          @NotNull GitVersion version) {
    myProject = project;
    myHandler = handler;
    myVersion = version;
  }

  public static @NotNull GitHandlerAuthenticationManager prepare(@NotNull Project project,
                                                                 @NotNull GitLineHandler handler,
                                                                 @NotNull GitVersion version) throws IOException {
    GitHandlerAuthenticationManager manager = new GitHandlerAuthenticationManager(project, handler, version);
    GitUtil.tryRunOrClose(manager, () -> {
      manager.prepareHttpAuth();
      manager.prepareNativeSshAuth();
      manager.prepareGpgAgentAuth();
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
    Disposer.dispose(myDisposable);
  }

  private void prepareHttpAuth() throws IOException {
    GitHttpAuthService service = ApplicationManager.getApplication().getService(GitHttpAuthService.class);
    addHandlerPathToEnvironment(GitCommand.GIT_ASK_PASS_ENV, service);
    AuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), PassthroughAuthenticationGate.getInstance());
    GitHttpAuthenticator httpAuthenticator = service.createAuthenticator(myProject,
                                                                         myHandler.getUrls(),
                                                                         myHandler.getWorkingDirectory(),
                                                                         authenticationGate,
                                                                         myHandler.getIgnoreAuthenticationMode());
    myHttpHandler = service.registerHandler(httpAuthenticator, myDisposable);
    myHandler.addCustomEnvironmentVariable(GitAskPassAppHandler.IJ_ASK_PASS_HANDLER_ENV, myHttpHandler.toString());
    int port = service.getIdePort();
    myHandler.addCustomEnvironmentVariable(GitAskPassAppHandler.IJ_ASK_PASS_PORT_ENV, Integer.toString(port));

    myHandler.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(@NonNls String line, Key outputType) {
        if (!httpAuthenticator.wasRequested()) {
          return;
        }

        String lowerCaseLine = StringUtil.toLowerCase(line);
        if (lowerCaseLine.contains("authentication failed") ||
            lowerCaseLine.contains("403 forbidden") ||
            lowerCaseLine.contains("but was used to access one of realms") ||
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

    boolean useSchannel = SystemInfo.isWindows &&
                          GitVersionSpecialty.CAN_USE_SCHANNEL.existsIn(myVersion) &&
                          AdvancedSettings.getBoolean("git.use.schannel.on.windows");
    if (useSchannel) {
      myHandler.overwriteConfig("http.sslBackend=schannel");
    }
  }

  public boolean isHttpAuthFailed() {
    return myHttpAuthFailed;
  }

  private void prepareNativeSshAuth() throws IOException {
    NativeSshAuthService service = NativeSshAuthService.getInstance();

    boolean doNotRememberPasswords = myHandler.getUrls().size() > 1;
    AuthenticationGate authenticationGate = notNull(myHandler.getAuthenticationGate(), PassthroughAuthenticationGate.getInstance());
    NativeSshGuiAuthenticator authenticator =
      new NativeSshGuiAuthenticator(myProject, authenticationGate, myHandler.getIgnoreAuthenticationMode(), doNotRememberPasswords);

    myNativeSshHandler = service.registerHandler(authenticator, myDisposable);
    int port = service.getIdePort();

    addHandlerPathToEnvironment(GitCommand.GIT_SSH_ASK_PASS_ENV, service);
    myHandler.addCustomEnvironmentVariable(NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_HANDLER_ENV, myNativeSshHandler.toString());
    myHandler.addCustomEnvironmentVariable(NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_PORT_ENV, Integer.toString(port));

    myHandler.addCustomEnvironmentVariable(GitCommand.SSH_ASKPASS_REQUIRE_ENV, "force");
    // SSH_ASKPASS_REQUIRE is supported by openssh 8.4+, prior versions ignore SSH_ASKPASS if DISPLAY variable is not set
    String displayEnv = StringUtil.nullize(System.getenv(GitCommand.DISPLAY_ENV));
    myHandler.addCustomEnvironmentVariable(GitCommand.DISPLAY_ENV, StringUtil.notNullize(displayEnv, ":0.0"));

    if (Registry.is("git.use.setsid.for.native.ssh")) {
      // if SSH_ASKPASS_REQUIRE is not supported, openssh will prioritize tty used to spawn IDE to SSH_ASKPASS. Detach it with setsid.
      myHandler.withNoTty();
    }
  }

  private void prepareGpgAgentAuth() throws IOException {
    if (!GpgAgentConfigurator.isEnabled(myHandler.myExecutable)) {
      return;
    }
    Project project = myHandler.project();
    VirtualFile root = myHandler.getExecutableContext().getRoot();
    if (project == null || root == null) {
      return;
    }

    GitCommand command = myHandler.getCommand();
    boolean needGpgSigning =
      (command == GitCommand.COMMIT || command == GitCommand.TAG || command == GitCommand.MERGE) &&
      GitGpgConfigUtilsKt.isGpgSignEnabled(project, root);

    if (needGpgSigning) {
      PinentryService.PinentryData pinentryData = PinentryService.getInstance(project).startSession();
      if (pinentryData != null) {
        myHandler.addCustomEnvironmentVariable(PinentryService.PINENTRY_USER_DATA_ENV, pinentryData.toString());
        myHandler.addListener(new GitHandlerListener() {
          @Override
          public void processTerminated(int exitCode) {
            PinentryService.getInstance(project).stopSession();
          }
        });
      }
    }
  }

  private void addHandlerPathToEnvironment(@NotNull String env,
                                           @NotNull ExternalProcessHandlerService<?> service) throws IOException {
    GitExecutable executable = myHandler.getExecutable();
    File scriptFile = service.getCallbackScriptPath(executable.getId(),
                                                    new GitScriptGenerator(executable),
                                                    shouldUseBatchScript(executable));
    String scriptPath = executable.convertFilePath(scriptFile);
    myHandler.addCustomEnvironmentVariable(env, scriptPath);
  }

  private boolean shouldUseBatchScript(@NotNull GitExecutable executable) {
    if (!SystemInfo.isWindows) return false;
    if (!executable.isLocal()) return false;
    if (Registry.is("git.use.shell.script.on.windows") &&
        GitVersionSpecialty.CAN_USE_SHELL_HELPER_SCRIPT_ON_WINDOWS.existsIn(myVersion)) {
      return isCustomSshExecutableConfigured();
    }
    return true;
  }

  private boolean isCustomSshExecutableConfigured() {
    String sshCommand = readSshCommand();
    String command = StringUtil.trim(StringUtil.unquoteString(StringUtil.notNullize(sshCommand)));
    // do not treat 'ssh -vvv' as custom executable
    return !command.isEmpty() && !command.startsWith("ssh ");
  }

  @Nullable
  private String readSshCommand() {
    String sshCommand = EnvironmentUtil.getValue(GitCommand.GIT_SSH_COMMAND_ENV);
    if (sshCommand != null) return sshCommand;

    sshCommand = EnvironmentUtil.getValue(GitCommand.GIT_SSH_ENV);
    if (sshCommand != null) return sshCommand;

    VirtualFile root = myHandler.getExecutableContext().getRoot();
    if (root == null) return null;

    GitRepository repo = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
    if (repo != null) {
      return GitProjectConfigurationCache.getInstance(myProject).readRepositoryConfig(repo, GitConfigUtil.CORE_SSH_COMMAND);
    }
    else {
      try {
        return GitConfigUtil.getValue(myProject, root, GitConfigUtil.CORE_SSH_COMMAND);
      }
      catch (VcsException e) {
        LOG.warn(e);
        return null;
      }
    }
  }
}
