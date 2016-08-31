package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AnsiEscapeDecoderTest extends PlatformTestCase {

  public void testTextWithoutColors() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("", ProcessOutputTypes.STDOUT, createExpectedAcceptor(
      Pair.create("", ProcessOutputTypes.STDOUT)
    ));
    decoder.escapeText("simple text", ProcessOutputTypes.STDOUT, createExpectedAcceptor(
      Pair.create("simple text", ProcessOutputTypes.STDOUT)
    ));
  }

  public void testSingleColoredChunk() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("Chrome 35.0.1916 (Linux): Executed 0 of 1\u001B[32m SUCCESS\u001B[39m (0 secs / 0 secs)\n", ProcessOutputTypes.STDOUT, createExpectedAcceptor(
      Pair.create("Chrome 35.0.1916 (Linux): Executed 0 of 1", ProcessOutputTypes.STDOUT),
      Pair.create(" SUCCESS", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[32m")),
      Pair.create(" (0 secs / 0 secs)\n", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[39m"))
    ));
  }

  public void testCompoundEscSeq() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("E\u001B[41m\u001B[37mE\u001B[0mE", ProcessOutputTypes.STDOUT, createExpectedAcceptor(
      Pair.create("E", ProcessOutputTypes.STDOUT),
      Pair.create("E", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[41;37m")),
      Pair.create("E", ProcessOutputTypes.STDOUT)
    ));
  }

  public void testOtherEscSeq() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText("Plain\u001B[32mGreen\u001B[39mNormal\u001B[1A\u001B[2K\u001B[31mRed\u001B[39m",
                       ProcessOutputTypes.STDOUT,
                       createExpectedAcceptor(
                         Pair.create("Plain", ProcessOutputTypes.STDOUT),
                         Pair.create("Green", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[32m")),
                         Pair.create("Normal", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[39m")),
                         Pair.create("Red", ColoredOutputTypeRegistry.getInstance().getOutputKey("\u001B[31m"))
                       )
    );
  }

  public void testBackspaceControlSequence() throws Exception {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText(" 10% 0/1 build modules\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b 70% 1/1 build modules",
                       ProcessOutputTypes.STDERR,
                       createExpectedAcceptor(
                         Pair.create(" 70% 1/1 build modules", ProcessOutputTypes.STDERR)
                       )
    );
    decoder.escapeText("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b 40% 1/2 build modules\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b 30% 1/3 build modules\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b 25% 1/4 build modules",
                       ProcessOutputTypes.STDERR,
                       createExpectedAcceptor(
                         Pair.create("\n 25% 1/4 build modules", ProcessOutputTypes.STDERR)
                       )
    );
  }

  @NotNull
  private static List<Pair<String, String>> toListWithKeyName(@NotNull Collection<Pair<String, Key>> list) {
    return ContainerUtil.map(list, pair -> Pair.create(pair.first, pair.second.toString()));
  }

  private static AnsiEscapeDecoder.ColoredChunksAcceptor createExpectedAcceptor(@NotNull final Pair<String, Key>... expected) {
    return new AnsiEscapeDecoder.ColoredChunksAcceptor() {
      @Override
      public void coloredChunksAvailable(List<Pair<String, Key>> chunks) {
        List<Pair<String, String>> expectedWithKeyName = toListWithKeyName(Arrays.asList(expected));
        List<Pair<String, String>> actualWithKeyName = toListWithKeyName(chunks);
        Assert.assertEquals(expectedWithKeyName, actualWithKeyName);
      }

      @Override
      public void coloredTextAvailable(String text, Key attributes) {
        throw new RuntimeException(); // shouldn't be called
      }
    };
  }
}
