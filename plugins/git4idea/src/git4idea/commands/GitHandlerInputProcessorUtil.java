// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * Various factory methods for {@link GitHandler#setInputProcessor(ThrowableConsumer)}
 */
public final class GitHandlerInputProcessorUtil {
  private static final String DEFAULT_SEPARATOR = "\n";

  private GitHandlerInputProcessorUtil() {
  }

  public static @NotNull ThrowableConsumer<OutputStream, IOException> writeLines(@NotNull Collection<String> lines, @NotNull Charset charset) {
    return writeLines(lines, DEFAULT_SEPARATOR, charset, false);
  }

  /**
   * Creates an input processor for {@link GitHandler} which sends provided lines to the process input stream.
   *
   * @see GitHandler#setInputProcessor(ThrowableConsumer)
   *
   * @param lines data to send to the stream
   * @param separator string to separate lines with
   * @param charset charset to use
   * @param endWithSecondSeparator send an additional separator to the output to indicate the end of data.
   *                               On Windows, the output stream won't be closed when this parameter is set to {@code true}.
   * @return an input processor (a {@link ThrowableConsumer} instance) that writes provided lines to the {@link OutputStream} passed to it.
   */
  public static @NotNull ThrowableConsumer<OutputStream, IOException> writeLines(
    @NotNull Collection<String> lines,
    @NotNull String separator,
    @NotNull Charset charset,
    boolean endWithSecondSeparator
  ) {
    return stream -> {
      try (OutputStreamWriter writer = new OutputStreamWriter(stream, charset)) {
        for (String line : lines) {
          writer.write(line);
          writer.write(separator);
        }
        if (endWithSecondSeparator) writer.write(separator);
        writer.flush();
      }
    };
  }

  public static @NotNull ThrowableConsumer<OutputStream, IOException> redirectStream(@NotNull InputStream stream) {
    return outputStream -> {
      try (outputStream) {
        FileUtil.copy(stream, outputStream);
      }
    };
  }

  public static @NotNull ThrowableConsumer<OutputStream, IOException> empty() {
    return outputStream -> {
      outputStream.close();
    };
  }
}
