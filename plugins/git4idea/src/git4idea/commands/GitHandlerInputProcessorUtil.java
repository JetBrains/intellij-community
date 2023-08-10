// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.util.SystemInfo;
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

  @NotNull
  public static ThrowableConsumer<OutputStream, IOException> writeLines(@NotNull Collection<String> lines, @NotNull Charset charset) {
    return writeLines(lines, DEFAULT_SEPARATOR, charset, false);
  }

  /**
   * Creates an input processor for {@link GitHandler} which sends provided lines to the process input stream.
   * <p/>
   * When {@code endWithSecondSeparator} parameter is set to {@code true} on Windows os, the output stream is not closed.
   * This allows {@link com.intellij.execution.process.WinRunnerMediator} to send Ctrl+C
   * through process's stdin in order to softly kill it.
   *
   * @see GitHandler#setInputProcessor(ThrowableConsumer)
   * @see com.intellij.execution.process.WinRunnerMediator#sendCtrlEventThroughStream
   *
   * @param lines data to send to the stream
   * @param separator string to separate lines with
   * @param charset charset to use
   * @param endWithSecondSeparator send an additional separator to the output to indicate the end of data.
   *                               On Windows, the output stream won't be closed when this parameter is set to {@code true}.
   * @return an input processor (a {@link ThrowableConsumer} instance) that writes provided lines to the {@link OutputStream} passed to it.
   */
  @NotNull
  public static ThrowableConsumer<OutputStream, IOException> writeLines(@NotNull Collection<String> lines,
                                                                        @NotNull String separator,
                                                                        @NotNull Charset charset,
                                                                        boolean endWithSecondSeparator) {
    return stream -> {
      OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
      try {
        for (String line : lines) {
          writer.write(line);
          writer.write(separator);
        }
        if (endWithSecondSeparator) writer.write(separator);
        writer.flush();
      }
      finally {
        if (!SystemInfo.isWindows || !endWithSecondSeparator) writer.close();
      }
    };
  }

  @NotNull
  public static ThrowableConsumer<OutputStream, IOException> redirectStream(@NotNull InputStream stream) {
    return outputStream -> {
      try (outputStream) {
        FileUtil.copy(stream, outputStream);
      }
    };
  }
}
