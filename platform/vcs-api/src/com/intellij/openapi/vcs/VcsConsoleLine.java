// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class VcsConsoleLine {
  private final List<Pair<String, ConsoleViewContentType>> myChunks;

  private VcsConsoleLine(@NotNull List<Pair<String, ConsoleViewContentType>> chunks) {
    myChunks = chunks;
  }

  public void print(@NotNull ConsoleView console) {
    ConsoleViewContentType lastType = ConsoleViewContentType.NORMAL_OUTPUT;
    for (Pair<String, ConsoleViewContentType> chunk : myChunks) {
      console.print(chunk.first, chunk.second);
      lastType = chunk.second;
    }
    console.print("\n", lastType);
  }

  @Nullable
  public static VcsConsoleLine create(@Nullable String message, @NotNull ConsoleViewContentType contentType) {
    return create(Collections.singletonList(Pair.create(message, contentType)));
  }

  @Nullable
  public static VcsConsoleLine create(@NotNull List<Pair<String, ConsoleViewContentType>> lineChunks) {
    List<Pair<String, ConsoleViewContentType>> chunks = ContainerUtil.filter(lineChunks, it -> !StringUtil.isEmptyOrSpaces(it.first));
    if (chunks.isEmpty()) return null;
    return new VcsConsoleLine(chunks);
  }
}
