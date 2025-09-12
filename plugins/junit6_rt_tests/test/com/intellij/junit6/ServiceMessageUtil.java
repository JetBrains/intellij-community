// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;

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
}
