// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SerializationException extends RuntimeException {
  public SerializationException() {
  }

  public SerializationException(@NotNull String message, @NotNull Map<String, ?> context) {
    super(message + " (" + StringUtil.join(ContainerUtil.map(context.entrySet(), it -> it.getKey() + "=" + it.getValue()), ", ") + ")");
  }

  public SerializationException(String message) {
    super(message);
  }

  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public SerializationException(Throwable cause) {
    super(cause);
  }
}
