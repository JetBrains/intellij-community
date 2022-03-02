package com.intellij.jps.cache.client;

import com.intellij.compiler.cache.client.JpsServerAuthExtension;
import com.intellij.jps.cache.JpsCacheBundle;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;
import static com.intellij.jps.cache.ui.JpsLoaderNotifications.ATTENTION;

public final class  JpsServerAuthUtil {
  private static final Logger LOG = Logger.getInstance(JpsServerAuthUtil.class);
  private static final Key<Boolean> NOTIFICATION_SHOWN_KEY = Key.create("AUTH_NOTIFICATION_SHOWN");

  public static void checkAuthenticatedInBackgroundThread(@NotNull Disposable parentDisposable,
                                                          @NotNull Project project,
                                                          @NotNull Runnable onAuthCompleted) {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(parentDisposable, disposable);
    JpsServerAuthExtension authExtension = JpsServerAuthExtension.getInstance();
    if (authExtension == null) {
      Boolean userData = project.getUserData(NOTIFICATION_SHOWN_KEY);
      if (userData == null) {
        project.putUserData(NOTIFICATION_SHOWN_KEY, Boolean.TRUE);
        ApplicationManager.getApplication().invokeLater(() -> {
          ATTENTION
            .createNotification(JpsCacheBundle.message("notification.title.jps.caches.downloader"), JpsCacheBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work.plugin"), NotificationType.WARNING)
            .setListener(NotificationListener.URL_OPENING_LISTENER)
            .notify(project);
        });
      }
      LOG.warn("JetBrains Internal Authentication plugin is required for the correct work. Please enable it.");
      return;
    }
    INSTANCE.execute(() -> {
      authExtension.checkAuthenticated("Jps Caches Downloader", disposable, () -> {
        Disposer.dispose(disposable);
        onAuthCompleted.run();
      });
    });
  }

  static @NotNull Map<String, String> getRequestHeaders() {
    JpsServerAuthExtension authExtension = JpsServerAuthExtension.getInstance();
    if (authExtension == null) {
      String message = JpsCacheBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work.plugin");
      throw new RuntimeException(message);
    }
    Map<String, String> authHeader = authExtension.getAuthHeader(false);
    if (authHeader == null) {
      String message = JpsCacheBundle.message("internal.authentication.plugin.missing.token");
      throw new RuntimeException(message);
    }
    return authHeader;
  }
}
