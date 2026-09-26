// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrintChildrenResult {
  public enum ChildrenAction {VISIT, REMOVE, REPLACE}

  public final ChildrenAction Action;
  public final @Nullable String ReplacementText;

  public static final PrintChildrenResult Visit = new PrintChildrenResult(ChildrenAction.VISIT, null);

  public static final PrintChildrenResult Remove = new PrintChildrenResult(ChildrenAction.REMOVE, null);

  public static PrintChildrenResult Replace(@NotNull String text) {
    return new PrintChildrenResult(ChildrenAction.REPLACE, text);
  }

  private PrintChildrenResult(ChildrenAction action, @Nullable String replacementText) {
    Action = action;
    ReplacementText = replacementText;
  }
}
