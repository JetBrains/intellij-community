// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServiceMessageUtil {
  public static @NotNull String replaceAttributes(@NotNull ServiceMessage message, @NotNull Map<String, String> attributes) {
    Map<String, String> attrs = new LinkedHashMap<>(message.getAttributes());
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (attrs.containsKey(entry.getKey())) {
        attrs.put(entry.getKey(), entry.getValue());
      }
    }
    return ServiceMessage.asString(message.getMessageName(), attrs);
  }

  public static @NotNull String normalizedTestOutput(@NotNull ServiceMessage message, @NotNull Map<String, String> attributes) {
    attributes = new HashMap<>(attributes);
    attributes.put("timestamp", "##timestamp##");
    attributes.put("duration", "##duration##");

    return replaceAttributes(message, attributes)
      .replaceAll("##teamcity\\[", "##TC[")
      .replaceAll("timestamp = [0-9\\-:.T]+", "timestamp = ##timestamp##");
  }
}
