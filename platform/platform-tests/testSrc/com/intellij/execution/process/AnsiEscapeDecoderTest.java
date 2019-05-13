package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnsiEscapeDecoderTest extends PlatformTestCase {

  private static final String STDOUT_KEY = ProcessOutputTypes.STDOUT.toString();
  private static final String STDERR_KEY = ProcessOutputTypes.STDERR.toString();

  public void testTextWithoutColors() {
    check(new ColoredText("", ProcessOutputTypes.STDOUT));
    check(new ColoredText("simple text", ProcessOutputTypes.STDOUT).addExpected("simple text", STDOUT_KEY));
  }

  public void testSingleColoredChunk() {
    check(new ColoredText("Chrome 35.0.1916 (Linux): Executed 0 of 1\u001B[32m SUCCESS\u001B[39m (0 secs / 0 secs)\n",
                          ProcessOutputTypes.STDOUT)
            .addExpected("Chrome 35.0.1916 (Linux): Executed 0 of 1", STDOUT_KEY)
            .addExpected(" SUCCESS", "\u001B[32m")
            .addExpected(" (0 secs / 0 secs)\n", "\u001B[39m"));
  }

  public void testCompoundEscSeq() {
    check(new ColoredText("E\u001B[41m\u001B[37mE\u001B[0mE", ProcessOutputTypes.STDOUT)
            .addExpected("E", STDOUT_KEY)
            .addExpected("E", "\u001B[41;37m")
            .addExpected("E", STDOUT_KEY));
  }

  public void testOtherEscSeq() {
    check(new ColoredText("Plain\u001B[32mGreen\u001B[39mNormal\u001B[1A\u001B[2K\u001B[31mRed\u001B[39m", ProcessOutputTypes.STDOUT)
            .addExpected("Plain", STDOUT_KEY)
            .addExpected("Green", "\u001B[32m")
            .addExpected("Normal", "\u001B[39m")
            .addExpected("Red", "\u001B[31m"));
  }

  public void testPrivateSequence() {
    check(new ColoredText("\u001B[0;32mgreen\u001B[0m\u001B[0K\u001B[?25l\n", ProcessOutputTypes.STDOUT)
            .addExpected("green", "\u001B[0;32m")
            .addExpected("\n", STDOUT_KEY)
    );
  }

  public void testMalformedSequence() {
    check(false, Collections.singletonList(new ColoredText("\u001B[32mGreen\u001B[\1World\n", ProcessOutputTypes.STDOUT)
            .addExpected("Green\u001B[\1World\n", "\u001B[32m")
    ));
    check(false, Collections.singletonList(new ColoredText("\u001B\n", ProcessOutputTypes.STDOUT)
            .addExpected("\u001B\n", ProcessOutputTypes.STDOUT.toString())
    ));
    check(false, Collections.singletonList(new ColoredText("\u001B[\n", ProcessOutputTypes.STDOUT)
            .addExpected("\u001B[\n", ProcessOutputTypes.STDOUT.toString())
    ));
    check(false, ContainerUtil.newArrayList(
      new ColoredText("\u001B\nHello,", ProcessOutputTypes.STDOUT)
        .addExpected("\u001B\nHello,", ProcessOutputTypes.STDOUT.toString()),
      new ColoredText("\u001B[31mWorld", ProcessOutputTypes.STDOUT)
        .addExpected("World", "\u001B[31m")
    ));
    check(false, ContainerUtil.newArrayList(
      new ColoredText("\u001BHello,", ProcessOutputTypes.STDOUT)
        .addExpected("\u001BHello,", ProcessOutputTypes.STDOUT.toString())
    ));
    check(false, ContainerUtil.newArrayList(
      new ColoredText("\u001B[Hello,", ProcessOutputTypes.STDOUT)
        .addExpected("ello,", ProcessOutputTypes.STDOUT.toString())
    ));
    check(false, ContainerUtil.newArrayList(
      new ColoredText("something[\u001B]asdf[\u001B[]", ProcessOutputTypes.STDOUT)
        .addExpected("something[\u001B]asdf[\u001B[]", ProcessOutputTypes.STDOUT.toString())
    ));
  }

  public void testIncompleteEscapeSequences() {
    check(true, ContainerUtil.newArrayList(
      new ColoredText("\u001B", ProcessOutputTypes.STDOUT),
      new ColoredText("[33m Hello\u001B[3", ProcessOutputTypes.STDOUT).addExpected(" Hello", "\u001B[33m"),
      new ColoredText("4m, Work!", ProcessOutputTypes.STDOUT).addExpected(", Work!", "\u001B[34m")
    ));

    check(true, ContainerUtil.newArrayList(
      new ColoredText("\u001B[1;33m<\u001B[34mnamespace\u001B[1", ProcessOutputTypes.STDOUT)
        .addExpected("<", "\u001B[1;33m")
        .addExpected("namespace", "\u001B[34m"),
      new ColoredText(
        ";33m:abcd\u001B[0m\u001B[1;33m>\u001B[0m0\u001B[1;33m</\u001B[34mnamespace\u001B[1;33m:abcd\u001B[0m\u001B[1;33m>\u001B[0m",
        ProcessOutputTypes.STDOUT)
        .addExpected(":abcd", "\u001B[1;33m")
        .addExpected(">", "\u001B[0;1;33m")
        .addExpected("0", "stdout")
        .addExpected("</", "\u001B[1;33m")
        .addExpected("namespace", "\u001B[34m")
        .addExpected(":abcd", "\u001B[1;33m")
        .addExpected(">", "\u001B[0;1;33m")
    ));
    check(false, ContainerUtil.newArrayList(
      new ColoredText("\u001B[1;31m red\u001B[0m normal\u001B[1;32m green\u001B", ProcessOutputTypes.STDOUT)
        .addExpected(" red", "\u001B[1;31m")
        .addExpected(" normal", STDOUT_KEY)
        .addExpected(" green", "\u001B[1;32m"),
      new ColoredText("[0m\n", ProcessOutputTypes.STDOUT).addExpected("\n", STDOUT_KEY)
    ));
  }

  public void testStderr() {
    check(true, ContainerUtil.newArrayList(
      new ColoredText("\u001B[33m Hello,", ProcessOutputTypes.STDOUT).addExpected(" Hello,", "\u001B[33m"),
      new ColoredText("World!\n", ProcessOutputTypes.STDERR).addExpected("World!\n", STDERR_KEY),
      new ColoredText("\u001B[41m Changed stderr background", ProcessOutputTypes.STDERR)
        .addExpected(" Changed stderr background", "\u001B[41m"),
      new ColoredText("Unchanged stdout background", ProcessOutputTypes.STDOUT)
        .addExpected("Unchanged stdout background", "\u001B[33m")
    ));
  }

  private static void check(@NotNull ColoredText text) {
    check(true, Collections.singletonList(text));
  }

  private static void check(boolean testCharByCharProcessing, @NotNull List<ColoredText> texts) {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    List<Pair<String, String>> actualColoredChunks = ContainerUtil.newArrayList();
    //noinspection CodeBlock2Expr
    AnsiEscapeDecoder.ColoredTextAcceptor acceptor = (text, attributes) -> {
      actualColoredChunks.add(Pair.create(text, attributes.toString()));
    };
    for (ColoredText text : texts) {
      decoder.escapeText(text.myRawText, text.myOutputType, acceptor);
    }
    List<Pair<String, String>> expectedColoredChunks = new ArrayList<>();
    for (ColoredText text : texts) {
      expectedColoredChunks.addAll(text.myExpectedColoredChunks);
    }
    Assert.assertEquals(expectedColoredChunks, actualColoredChunks);

    if (testCharByCharProcessing) {
      // test stdout char by char
      actualColoredChunks.clear();
      decoder = new AnsiEscapeDecoder();
      for (ColoredText text : texts) {
        for (int i = 0; i < text.myRawText.length(); i++) {
          decoder.escapeText(String.valueOf(text.myRawText.charAt(i)), text.myOutputType, acceptor);
        }
      }
      expectedColoredChunks.clear();
      for (ColoredText text : texts) {
        for (Pair<String, String> chunk : text.myExpectedColoredChunks) {
          String chunkText = chunk.first;
          for (int i = 0; i < chunkText.length(); i++) {
            expectedColoredChunks.add(Pair.create(String.valueOf(chunkText.charAt(i)), chunk.second));
          }
        }
      }
      Assert.assertEquals(expectedColoredChunks, actualColoredChunks);
    }
  }

  @NotNull
  public static Process createTestProcess() {
    // have to be synchronised because used from pooled thread
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(10000);
    Semaphore finished = new Semaphore(1);
    return new Process() {
      @Override
      public OutputStream getOutputStream() {
        return outputStream;
      }

      @Override
      public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public int waitFor() {
        finished.waitFor();
        return 0;
      }

      @Override
      public int exitValue() {
        return 0;
      }

      @Override
      public void destroy() {
        finished.up();
      }
    };
  }

  public void testPerformance() {
    Process testProcess = createTestProcess();

    //noinspection CodeBlock2Expr
    withProcessHandlerFrom(testProcess, handler -> {
      PlatformTestUtil.startPerformanceTest("ansi color", 8_500, () -> {
        for (int i = 0; i < 2_000_000; i++) {
          handler.notifyTextAvailable(i + "Chrome 35.0.1916 (Linux): Executed 0 of 1\u001B[32m SUCCESS\u001B[39m (0 secs / 0 secs)\n",
                                      ProcessOutputTypes.STDOUT);
          handler.notifyTextAvailable(i + "Plain\u001B[32mGreen\u001B[39mNormal\u001B[1A\u001B[2K\u001B[31mRed\u001B[39m\n",
                                      ProcessOutputTypes.SYSTEM);
        }
      }).assertTiming();
    });
  }

  public static void withProcessHandlerFrom(@NotNull Process testProcess, @NotNull Consumer<? super ProcessHandler> actionToTest) {
    KillableColoredProcessHandler handler = new KillableColoredProcessHandler(testProcess, "testProcess");
    handler.setShouldDestroyProcessRecursively(false);
    handler.setShouldKillProcessSoftly(false);
    handler.startNotify();
    handler.notifyTextAvailable("Running stuff...\n", ProcessOutputTypes.STDOUT);

    try {
      actionToTest.consume(handler);
    }
    finally {
      handler.destroyProcess();
      handler.waitFor();
    }
  }

  private static class ColoredText {
    private final String myRawText;
    private final List<Pair<String, String>> myExpectedColoredChunks = new ArrayList<>();
    private final Key myOutputType;

    ColoredText(@NotNull String rawText, @NotNull Key outputType) {
      myRawText = rawText;
      myOutputType = outputType;
    }

    private ColoredText addExpected(@NotNull String text, @NotNull String colorKey) {
      myExpectedColoredChunks.add(Pair.create(text, colorKey));
      return this;
    }
  }
}
