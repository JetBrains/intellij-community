// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
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

  void invokeJBALogin(@Nullable Consumer<? super String> userIdConsumer, @Nullable Runnable onFailure);

  /**
   * Starts the auth flow by opening the browser and waiting for the user to proceed with logging in.
   */
  @SuppressWarnings("unused")
  @NotNull LoginSession startLoginSession(@NotNull LoginMode loginMode);

  @SuppressWarnings("unused")
  @NotNull CompletableFuture<@NotNull LicenseListResult> getAvailableLicenses(@NotNull String productCode);

  static @Nullable JBAccountInfoService getInstance() {
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
    @NotNull LicenseType type,
    @NotNull String licensedTo,
    @NotNull LocalDate expiresOn
  ) { }

  enum LicenseType {
    UNKNOWN,
    COMPANY,
    INDIVIDUAL,
    STUDENT,
    OPENSOURCE,
    CLASSROOM,
    HOBBY,
  }

  sealed interface LicenseListResult permits LicenseListResult.FetchFailure,
                                             LicenseListResult.LicenseList,
                                             LicenseListResult.LoginRequired {
    record LicenseList(@NotNull List<@NotNull JbaLicense> licenses) implements LicenseListResult { }

    final class LoginRequired implements LicenseListResult {
      public static final LoginRequired INSTANCE = new LoginRequired();
    }

    record FetchFailure(@NotNull String errorMessage) implements LicenseListResult { }
  }

  interface AuthStateListener extends EventListener {
    @NotNull Topic<AuthStateListener> TOPIC = new Topic<>(AuthStateListener.class);

    void authStateChanged(@Nullable JBAData newState);
  }
}
