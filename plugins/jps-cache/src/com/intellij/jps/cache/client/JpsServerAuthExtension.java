package com.intellij.jps.cache.client;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

/**
 * Extension point which provides authentication data for requests to the JPS cache server
 */
public interface JpsServerAuthExtension {
  ExtensionPointName<JpsServerAuthExtension> EP_NAME = ExtensionPointName.create("com.intellij.jpsServerAuthExtension");

  /**
   * This method should check if the user was authenticated, if not it should do any needed actions to provide
   * auth token for further requests. This method will be called outside of the EDT and should be asynchronous.
   * If the user was authenticated the callback should be invoked.
   *
   * @param presentableReason reason for the token request
   * @param onAuthCompleted callback on authentication complete, if token already exists it also should be invoked
   */
   void checkAuthenticated(@NotNull String presentableReason, @NotNull Runnable onAuthCompleted);

  /**
   * The method provides HTTP authentication headers for the requests to the server.
   * It will be called in the background thread. The assertion that thread isn't EDT can
   * be added to the implementation. If it's not possible to get the authentication headers,
   * empty map or `null` can be return.
   * @return
   */
  Map<String, String> getAuthHeader();

  @NotNull
  static JpsServerAuthExtension getInstance() {
    return EP_NAME.findExtensionOrFail(JpsServerAuthExtension.class);
  }

  static void checkAuthenticatedInBackgroundThread(@NotNull Runnable onAuthCompleted) {
    INSTANCE.execute(() -> getInstance().checkAuthenticated("Jps Caches Downloader", onAuthCompleted));
  }
}