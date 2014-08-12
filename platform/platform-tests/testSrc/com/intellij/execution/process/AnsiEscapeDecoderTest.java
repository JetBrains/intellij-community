package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

public class AnsiEscapeDecoderTest extends PlatformTestCase {

  public void testTextWithoutColors() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("", ProcessOutputTypes.STDOUT, createExpectedAcceptor(ContainerUtil.newArrayList(
      Pair.create("", ProcessOutputTypes.STDOUT)
    )));
    decoder.escapeText("simple text", ProcessOutputTypes.STDOUT, createExpectedAcceptor(ContainerUtil.newArrayList(
      Pair.create("simple text", ProcessOutputTypes.STDOUT)
    )));
  }

  public void testSingleColoredChunk() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("Chrome 35.0.1916 (Linux): Executed 0 of 1\u001B[32m SUCCESS\u001B[39m (0 secs / 0 secs)\n", ProcessOutputTypes.STDOUT, createExpectedAcceptor(ContainerUtil.newArrayList(
      Pair.create("Chrome 35.0.1916 (Linux): Executed 0 of 1", ProcessOutputTypes.STDOUT),
      Pair.create(" SUCCESS", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[32m")),
      Pair.create(" (0 secs / 0 secs)\n", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[39m"))
    )));
  }

  public void testCompoundEscSeq() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("E\u001B[41m\u001B[37mE\u001B[0mE", ProcessOutputTypes.STDOUT, createExpectedAcceptor(ContainerUtil.newArrayList(
      Pair.create("E", ProcessOutputTypes.STDOUT),
      Pair.create("E", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[41;37m")),
      Pair.create("E", ProcessOutputTypes.STDOUT)
    )));
  }

  private static AnsiEscapeDecoder.ColoredChunksAcceptor createExpectedAcceptor(@NotNull final List<Pair<String, Key>> expected) {
    return new AnsiEscapeDecoder.ColoredChunksAcceptor() {
      @Override
      public void coloredChunksAvailable(List<Pair<String, Key>> chunks) {
        Assert.assertEquals(expected, chunks);
      }

      @Override
      public void coloredTextAvailable(String text, Key attributes) {
        throw new RuntimeException(); // shouldn't be called
      }
    };
  }
}
