// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.IntBasedStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The message of the lexer tests for a lexer state that the highlighter storage changes.
 * {@code LexerTestCase} and the JUnit 5 lexer fixtures use it.
 */
@ApiStatus.Internal
public final class HighlighterStorageCheck {
  private HighlighterStorageCheck() {
  }

  /**
   * Returns the message for a state that changes after a round trip through the highlighter storage.
   * {@code passStorageHint} tells how the test gives its own storage to the check.
   */
  public static @NotNull String mismatchMessage(int offset,
                                                 int state,
                                                 int storedState,
                                                 @NotNull DataStorage storage,
                                                 @NotNull String passStorageHint) {
    String storageName = storage.getClass().getName();
    StringBuilder message = new StringBuilder();
    message.append(String.format("The state 0x%08X (%d) at offset %d changes to 0x%08X (%d) in %s.\n",
                                 state, state, offset, storedState, storedState, storageName));
    message.append("The editor highlighter keeps the lexer state in this storage. ")
      .append("With a changed state, the highlighter restarts the lexer in a wrong state.\n");
    message.append("Do one of these:\n");
    message.append("1. If the editor highlighter of the language uses a different storage, ").append(passStorageHint).append(".\n");
    message.append("2. Find the lexer states that do not fit in the storage, and pack them in fewer bits.");
    if (storage.getClass() == IntBasedStorage.class) {
      message.append(" IntBasedStorage keeps 16 bits of the state with the sign, from -32768 to 32767.")
        .append(" Look for a flag or a counter at bit 15 or higher.");
    }
    message.append("\n");
    message.append("3. If the lexer needs all of these bits, override LexerEditorHighlighter.createStorage() ")
      .append("to return a storage that keeps the full state. ")
      .append("Use an existing storage when it is sufficient, or extend IntArrayDataStorage as TextMateLexerDataStorage does. ")
      .append("Then ").append(passStorageHint).append(".");
    return message.toString();
  }
}
