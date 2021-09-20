package com.intellij.jps.cache.client;

import com.intellij.jps.cache.JpsCacheBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class JpsServerAuthUtil {
  static @NotNull Map<String, String> getRequestHeaders() {
    JpsServerAuthExtension authExtension = JpsServerAuthExtension.getInstance();
    if (authExtension == null) {
      String message = JpsCacheBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work.plugin");
      throw new RuntimeException(message);
    }
    Map<String, String> authHeader = authExtension.getAuthHeader();
    if (authHeader == null) {
      String message = JpsCacheBundle.message("internal.authentication.plugin.missing.token");
      throw new RuntimeException(message);
    }
    return authHeader;
  }
}
