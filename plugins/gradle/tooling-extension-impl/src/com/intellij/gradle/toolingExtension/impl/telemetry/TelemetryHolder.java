// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.telemetry;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class TelemetryHolder implements Serializable {

  private final @NotNull GradleTelemetryFormat format;
  private final byte[] traces;

  public TelemetryHolder(@NotNull GradleTelemetryFormat format, byte[] traces) {
    this.format = format;
    this.traces = traces;
  }

  public @NotNull GradleTelemetryFormat getFormat() {
    return format;
  }

  public byte[] getTraces() {
    return traces;
  }

  public static @NotNull TelemetryHolder empty() {
    return new TelemetryHolder(GradleTelemetryFormat.PROTOBUF, ArrayUtilRt.EMPTY_BYTE_ARRAY);
  }
}
