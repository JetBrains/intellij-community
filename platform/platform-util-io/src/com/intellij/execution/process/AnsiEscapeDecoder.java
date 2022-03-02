// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.process.AnsiStreamingLexer.AnsiElementType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * See <a href="http://en.wikipedia.org/wiki/ANSI_escape_code">ANSI escape code</a>.
 */
public class AnsiEscapeDecoder {
  private final ColoredOutputTypeRegistry myColoredOutputTypeRegistry = ColoredOutputTypeRegistry.getInstance();

  private final AnsiStreamingLexer myStdoutLexer = new AnsiStreamingLexer();
  private final AnsiStreamingLexer myStderrLexer = new AnsiStreamingLexer();
  private final AnsiTerminalEmulator myStdoutEmulator = new AnsiTerminalEmulator();
  private final AnsiTerminalEmulator myStderrEmulator = new AnsiTerminalEmulator();

  /**
   * Parses ansi-color codes from text and sends text fragments with color attributes to textAcceptor
   *
   * @param text         a string with ANSI escape sequences
   * @param outputType   stdout/stderr/system (from {@link ProcessOutputTypes})
   * @param textAcceptor receives text fragments with color attributes.
   *                     It can implement ColoredChunksAcceptor to receive list of pairs (text, attribute).
   */
  public void escapeText(@NotNull String text, @NotNull Key outputType, @NotNull ColoredTextAcceptor textAcceptor) {
    AnsiStreamingLexer effectiveLexer;
    AnsiTerminalEmulator effectiveEmulator;
    if (ProcessOutputType.isStdout(outputType)) {
      effectiveLexer = myStdoutLexer;
      effectiveEmulator = myStdoutEmulator;
    }
    else if (ProcessOutputType.isStderr(outputType)) {
      effectiveLexer = myStderrLexer;
      effectiveEmulator = myStderrEmulator;
    }
    else {
      processTextWithoutAnsi(text, outputType, textAcceptor);
      return;
    }

    effectiveLexer.append(text);
    effectiveLexer.advance();

    List<Pair<String, Key>> chunks = null;
    AnsiElementType elementType;
    while ((elementType = effectiveLexer.getElementType()) != null) {
      String elementText = effectiveLexer.getElementTextSmart();
      assert elementText != null;
      if (elementType == AnsiStreamingLexer.SGR) {
        effectiveEmulator.processSgr(elementText);
      }
      else if (elementType == AnsiStreamingLexer.TEXT) {
        chunks = processTextChunk(chunks, elementText, outputType, textAcceptor);
      }
      else {
        assert elementType == AnsiStreamingLexer.CONTROL;
        // Commands other than SGR are unhandled currently. Extend here when it's needed.
      }
      effectiveLexer.advance();
    }
    if (chunks != null && textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(chunks);
    }
  }

  /**
   * Fallback method for the {@link ProcessOutputTypes#SYSTEM system} output type. ANSI is not supported
   */
  private void processTextWithoutAnsi(@NotNull String text, @NotNull Key outputType, @NotNull ColoredTextAcceptor textAcceptor) {
    List<Pair<String, Key>> chunks = processTextChunk(null, text, outputType, textAcceptor);
    if (chunks != null && textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(chunks);
    }
  }

  @Nullable
  private List<Pair<String, Key>> processTextChunk(@Nullable List<Pair<String, Key>> buffer,
                                                   @NotNull String text,
                                                   @NotNull Key outputType,
                                                   @NotNull ColoredTextAcceptor textAcceptor) {
    Key attributes = getCurrentOutputAttributes(outputType);
    if (textAcceptor instanceof ColoredChunksAcceptor) {
      if (buffer == null) {
        buffer = new ArrayList<>(1);
      }
      buffer.add(Pair.create(text, attributes));
    }
    else {
      textAcceptor.coloredTextAvailable(text, attributes);
    }
    return buffer;
  }

  @NotNull
  protected Key getCurrentOutputAttributes(@NotNull Key outputType) {
    if (ProcessOutputType.isStdout(outputType)) {
      return myStdoutEmulator.isInitialState() ? outputType : myColoredOutputTypeRegistry.getOutputType(myStdoutEmulator, outputType);
    }
    if (ProcessOutputType.isStderr(outputType)) {
      return myStderrEmulator.isInitialState() ? outputType : myColoredOutputTypeRegistry.getOutputType(myStderrEmulator, outputType);
    }
    return outputType;
  }

  /**
   * @deprecated use {@link ColoredTextAcceptor} instead
   */
  @Deprecated(forRemoval = true)
  public interface ColoredChunksAcceptor extends ColoredTextAcceptor {
    void coloredChunksAvailable(@NotNull List<Pair<String, Key>> chunks);
  }

  @FunctionalInterface
  public interface ColoredTextAcceptor {
    void coloredTextAvailable(@NotNull String text, @NotNull Key attributes);
  }
}
