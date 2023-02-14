// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * important: ansi decoder returns indexed colors set using 30-37, 40-47, 90-97 and 100-107 codes using 38;5;color_number/48;5;color_number
 */
public class AnsiEscapeDecoderTest extends LightPlatformTestCase {

  private static final String STDOUT_KEY = ProcessOutputTypes.STDOUT.toString();
  private static final String STDERR_KEY = ProcessOutputTypes.STDERR.toString();

  public void testTextWithoutColors() {
    check(new ColoredText("", ProcessOutputTypes.STDOUT));
    check(new ColoredText("simple text", ProcessOutputTypes.STDOUT).addExpected("simple text", STDOUT_KEY));
  }

  public void testSequentialSgr() {
    check(new ColoredText("This is \u001B[1;3mItalic Bold", ProcessOutputTypes.STDOUT)
            .addExpected("This is ", STDOUT_KEY)
            .addExpected("Italic Bold", "1;3m")
    );
  }

  public void testSequentialSgrOneByOne() {
    check(new ColoredText("This is \u001B[1m\u001B[3mItalic Bold", ProcessOutputTypes.STDOUT)
            .addExpected("This is ", STDOUT_KEY)
            .addExpected("Italic Bold", "1;3m")
    );
  }

  public void testSingleColoredChunk() {
    check(new ColoredText("Chrome 35.0.1916 (Linux): Executed 0 of 1\u001B[32m SUCCESS\u001B[39m (0 secs / 0 secs)\n",
                          ProcessOutputTypes.STDOUT)
            .addExpected("Chrome 35.0.1916 (Linux): Executed 0 of 1", STDOUT_KEY)
            .addExpected(" SUCCESS", "38;5;2m")
            .addExpected(" (0 secs / 0 secs)\n", STDOUT_KEY));
  }

  public void testCompoundEscSeq() {
    check(new ColoredText("E\u001B[41m\u001B[37mE\u001B[0mE", ProcessOutputTypes.STDOUT)
            .addExpected("E", STDOUT_KEY)
            .addExpected("E", "48;5;1;38;5;7m")
            .addExpected("E", STDOUT_KEY));
  }

  public void testOtherEscSeq() {
    check(new ColoredText("Plain\u001B[32mGreen\u001B[39mNormal\u001B[1A\u001B[2K\u001B[31mRed\u001B[39m", ProcessOutputTypes.STDOUT)
            .addExpected("Plain", STDOUT_KEY)
            .addExpected("Green", "38;5;2m")
            .addExpected("Normal", STDOUT_KEY)
            .addExpected("Red", "38;5;1m"));
  }

  public void testPrivateSequence() {
    check(new ColoredText("\u001B[0m\u001B[32mgreen\u001B[0m\u001B[0K\u001B[?25l\n", ProcessOutputTypes.STDOUT)
            .addExpected("green", "38;5;2m")
            .addExpected("\n", STDOUT_KEY)
    );
  }

  public void testMalformedSequence1() {
    check(false, Collections.singletonList(new ColoredText("\u001B[32mGreen\u001B[\1World\n", ProcessOutputTypes.STDOUT)
                                             .addExpected("Green", "38;5;2m")
                                             .addExpected("\u001B[\1World\n", "38;5;2m")
    ));
  }

  public void testMalformedSequence2() {
    check(false, Collections.singletonList(new ColoredText("\u001B\n", ProcessOutputTypes.STDOUT)
                                             .addExpected("\u001B\n", ProcessOutputTypes.STDOUT.toString())
    ));
  }

  public void testMalformedSequence3() {
    check(false, Collections.singletonList(new ColoredText("\u001B[\n", ProcessOutputTypes.STDOUT)
                                             .addExpected("\u001B[\n", ProcessOutputTypes.STDOUT.toString())
    ));
  }

  public void testMalformedSequence4() {
    check(false, List.of(new ColoredText("\u001B\nHello,", ProcessOutputTypes.STDOUT)
                           .addExpected("\u001B\nHello,", STDOUT_KEY),
                         new ColoredText("\u001B[31mWorld", ProcessOutputTypes.STDOUT)
                           .addExpected("World", "38;5;1m")));
  }

  public void testMalformedSequence5() {
    check(false, List.of(new ColoredText("\u001BHello,", ProcessOutputTypes.STDOUT)
      .addExpected("\u001BHello,", STDOUT_KEY)));
  }

  public void testMalformedSequence6() {
    check(false, List.of(new ColoredText("\u001B[Hello,", ProcessOutputTypes.STDOUT)
      .addExpected("ello,", STDOUT_KEY)));
  }

  public void testMalformedSequence7() {
    check(false, List.of(new ColoredText("something[\u001B]asdf[\u001B[]", ProcessOutputTypes.STDOUT)
      .addExpected("something[", STDOUT_KEY)
      .addExpected("\u001B]asdf[", STDOUT_KEY)));
  }

  public void testIncompleteEscapeSequence1() {
    check(true, List.of(new ColoredText("\u001B", ProcessOutputTypes.STDOUT),
                        new ColoredText("[33m Hello\u001B[3", ProcessOutputTypes.STDOUT)
                          .addExpected(" Hello", "38;5;3m"),
                        new ColoredText("4m, Work!", ProcessOutputTypes.STDOUT)
                          .addExpected(", Work!", "38;5;4m")));
  }

  public void testIncompleteEscapeSequence2() {

    check(true, List.of(new ColoredText("\u001B[1m\u001B[33m<" +
                                        "\u001B[34mnamespace" +
                                        "\u001B[1m", ProcessOutputTypes.STDOUT)
                          .addExpected("<", "1;38;5;3m")
                          .addExpected("namespace", "1;38;5;4m"),
                        new ColoredText("\u001B[33m:abcd" +
                                        "\u001B[0m" +
                                        "\u001B[1;33m>" +
                                        "\u001B[0m0" +
                                        "\u001B[1;33m</" +
                                        "\u001B[34mnamespace" +
                                        "\u001B[1;33m:abcd" +
                                        "\u001B[0;1;33m>" +
                                        "\u001B[0m",
                                        ProcessOutputTypes.STDOUT)
                          .addExpected(":abcd", "1;38;5;3m")
                          .addExpected(">", "1;38;5;3m")
                          .addExpected("0", STDOUT_KEY)
                          .addExpected("</", "1;38;5;3m")
                          .addExpected("namespace", "1;38;5;4m")
                          .addExpected(":abcd", "1;38;5;3m")
                          .addExpected(">", "1;38;5;3m")));
  }

  public void testIncompleteEscapeSequence3() {
    check(false, List.of(new ColoredText("\u001B[1m\u001B[31m red" +
                                         "\u001B[0m normal" +
                                         "\u001B[1m\u001B[32m green" +
                                         "\u001B", ProcessOutputTypes.STDOUT)
                           .addExpected(" red", "1;38;5;1m")
                           .addExpected(" normal", STDOUT_KEY)
                           .addExpected(" green", "1;38;5;2m"),
                         new ColoredText("[0m\n", ProcessOutputTypes.STDOUT)
                           .addExpected("\n", STDOUT_KEY)));
  }

  public void testStderr() {
    check(true, List.of(new ColoredText("\u001B[33m Hello,", ProcessOutputTypes.STDOUT)
                          .addExpected(" Hello,", "38;5;3m"),
                        new ColoredText("World!\n", ProcessOutputTypes.STDERR)
                          .addExpected("World!\n", STDERR_KEY),
                        new ColoredText("\u001B[41m Changed stderr background", ProcessOutputTypes.STDERR)
                          .addExpected(" Changed stderr background", "48;5;1m"),
                        new ColoredText("Unchanged stdout background", ProcessOutputTypes.STDOUT)
                          .addExpected("Unchanged stdout background", "38;5;3m")));
  }

  public void testReset() {
    check(true, List.of(new ColoredText("Hello \u001B[33m Colored \u001B[0m Normal", ProcessOutputTypes.STDOUT)
      .addExpected("Hello ", STDOUT_KEY)
      .addExpected(" Colored ", "38;5;3m")
      .addExpected(" Normal", STDOUT_KEY)));
    check(true, List.of(new ColoredText("Hello \u001B[33;41m Colored \u001B[m Normal", ProcessOutputTypes.STDOUT)
      .addExpected("Hello ", STDOUT_KEY)
      .addExpected(" Colored ", "48;5;1;38;5;3m")
      .addExpected(" Normal", STDOUT_KEY)));
    check(true, List.of(new ColoredText("Hello \u001B[33;41;m Not colored \u001B[m Normal", ProcessOutputTypes.STDOUT)
      .addExpected("Hello ", STDOUT_KEY)
      .addExpected(" Not colored ", STDOUT_KEY)
      .addExpected(" Normal", STDOUT_KEY)));
    check(true, List.of(new ColoredText("Hello \u001B[33;41m Colored \u001B[;m Normal", ProcessOutputTypes.STDOUT)
      .addExpected("Hello ", STDOUT_KEY)
      .addExpected(" Colored ", "48;5;1;38;5;3m")
      .addExpected(" Normal", STDOUT_KEY)));
  }

  public void testDECKPAM() {
    check(false, List.of(new ColoredText("\u001B=Hello", ProcessOutputTypes.STDOUT)
      .addExpected("Hello", STDOUT_KEY)));
  }

  private static void check(@NotNull ColoredText text) {
    check(true, Collections.singletonList(text));
  }

  private static void check(boolean testCharByCharProcessing, @NotNull List<ColoredText> texts) {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    List<Pair<String, String>> actualColoredChunks = new ArrayList<>();
    AnsiEscapeDecoder.ColoredTextAcceptor acceptor = (text, attributes) ->
      actualColoredChunks.add(Pair.create(text, StringUtil.trimStart(attributes.toString(), "\u001b[0;")));
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
