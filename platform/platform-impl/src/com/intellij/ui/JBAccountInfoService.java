// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.idea.AppMode;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@ApiStatus.Internal
public interface JBAccountInfoService {
  final class JBAData {
    public final @NotNull String id;
    public final @NlsSafe @Nullable String loginName;
    public final @NlsSafe @Nullable String email;
    public final @NlsSafe @Nullable String presentableName;

    public JBAData(@NotNull String userId, @Nullable String loginName, @Nullable String email, @Nullable String presentableName) {
      this.id = userId;
      this.loginName = loginName;
      this.email = email;
      this.presentableName = presentableName;
    }
  }

  @Nullable JBAccountInfoService.JBAData getUserData();

  default @Nullable String getIdToken() {
    return null;
  }

  default @NotNull Future<String> getAccessToken() {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * @deprecated Use {@link #startLoginSession} instead.
   */
  @Deprecated
  default void invokeJBALogin(@Nullable Consumer<? super String> userIdConsumer, @Nullable Runnable onFailure) {
    try {
      //noinspection resource
      LoginSession loginSession = startLoginSession(LoginMode.AUTO);
      loginSession.onCompleted()
        .thenAccept(result -> {
          if (result instanceof LoginResult.LoginSuccessful successful && userIdConsumer != null) {
            userIdConsumer.accept(successful.jbaUser().id);
          }
          if (result instanceof LoginResult.LoginFailed && onFailure != null) {
            onFailure.run();
          }
        });
    }
    catch (Throwable e) {
      Logger.getInstance(JBAccountInfoService.class).error(e);
      if (onFailure != null) {
        onFailure.run();
      }
    }
  }

  /**
   * Starts the auth flow by opening the browser and waiting for the user to proceed with logging in.
   */
  @SuppressWarnings("unused")
  @NotNull LoginSession startLoginSession(@NotNull LoginMode loginMode);

  @SuppressWarnings("unused")
  @NotNull CompletableFuture<@NotNull LicenseListResult> getAvailableLicenses(@NotNull String productCode);

  @SuppressWarnings("unused")
  @NotNull CompletableFuture<@NotNull LicenseListResult> issueTrialLicense(@NotNull String productCode, @NotNull List<String> consentOptions);

  static @Nullable JBAccountInfoService getInstance() {
    // see BackendJbaInfoServiceImpl
    if (AppMode.isRemoteDevHost()) {
      List<ClientAppSession> controllerSessions = ClientSessionsManager.getAppSessions(ClientKind.CONTROLLER);
      if (controllerSessions.size() != 1) {
        Logger log = Logger.getInstance(JBAccountInfoService.class);
        log.debug("No controller session");
        return null;
      }
      ClientAppSession controllerSession = controllerSessions.get(0);
      return controllerSession.getService(JBAccountInfoService.class);
    }
    return JBAccountInfoServiceHolder.INSTANCE;
  }

  enum LoginMode {
    /**
     * Open the auth URL in the browser, start the built-in server, and await for the auth callback.
     */
    AUTO,

    /**
     * Open the login dialog, show the auth URL so that the user can proceed with it in the browser,
     * expect the user to copy the resulting auth token into the dialog manually.
     */
    MANUAL,
  }

  interface LoginSession extends AutoCloseable {
    /**
     * The returned CompletableFuture can be used to await the completion of the auth flow,
     * either successful or erroneous. The future never completes exceptionally,
     * instead, {@link LoginResult.LoginFailed} is used to signal that something went wrong.
     * {@linkplain CompletableFuture#cancel(boolean) Cancelling} the future does not affect the login session,
     * or the other futures returned by separate invocations of this method.
     */
    @NotNull CompletableFuture<@NotNull LoginResult> onCompleted();

    /**
     * Closes the session cancelling any futures returned from {@link #onCompleted()}.
     */
    @Override
    void close();
  }

  sealed interface LoginResult permits LoginResult.LoginFailed, LoginResult.LoginSuccessful {
    record LoginSuccessful(@NotNull JBAData jbaUser) implements LoginResult { }
    record LoginFailed(@NotNull String errorMessage) implements LoginResult { }
  }

  record JbaLicense(
    @NotNull String licenseId,
    @NotNull JBAData jbaUser,
    @NotNull LicenseKind licenseKind,
    @NotNull LicenseeType licenseeType,
    @NotNull String licensedTo,
    @NotNull Instant expiresOn
  ) { }

  enum LicenseKind {
    STANDARD,
    TRIAL,
    FREE,
  }

  enum LicenseeType {
    UNKNOWN,
    COMPANY,
    INDIVIDUAL,
    STUDENT,
    OPENSOURCE,
    CLASSROOM,
  }

  sealed interface LicenseListResult permits LicenseListResult.LicenseList,
                                             LicenseListResult.LoginRequired,
                                             LicenseListResult.FetchFailure,
                                             LicenseListResult.TrialRejected {
    record LicenseList(@NotNull List<@NotNull JbaLicense> licenses) implements LicenseListResult { }

    enum LoginRequired implements LicenseListResult {
      INSTANCE
    }

    record TrialRejected(@NotNull Reason reason, @Nullable String url, @NotNull String message) implements LicenseListResult {
      public enum Reason {
        TRIAL_NOT_ALLOWED,
        PAYMENT_PROOF_REQUIRED,
      }
    }

    record FetchFailure(@NotNull String errorMessage) implements LicenseListResult { }
  }

  interface AuthStateListener extends EventListener {
    @NotNull Topic<AuthStateListener> TOPIC = new Topic<>(AuthStateListener.class);

    void authStateChanged(@Nullable JBAData newState);
  }
}
