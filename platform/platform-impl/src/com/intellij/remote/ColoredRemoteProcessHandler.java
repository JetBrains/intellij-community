// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class ColoredRemoteProcessHandler<T extends RemoteProcess> extends BaseRemoteProcessHandler<T> implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public ColoredRemoteProcessHandler(@NotNull T process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  public final void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }
}