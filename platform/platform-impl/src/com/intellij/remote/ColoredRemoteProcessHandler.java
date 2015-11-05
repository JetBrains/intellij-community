package com.intellij.remote;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class ColoredRemoteProcessHandler<T extends RemoteProcess> extends BaseRemoteProcessHandler<T> implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public ColoredRemoteProcessHandler(@NotNull T process,
                                     @Nullable String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  public final void notifyTextAvailable(String text, Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  @Override
  public void coloredTextAvailable(String text, Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }
}
