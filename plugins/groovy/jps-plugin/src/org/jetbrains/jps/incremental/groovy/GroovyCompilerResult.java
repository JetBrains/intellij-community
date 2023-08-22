// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.groovy.compiler.rt.OutputItem;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GroovyCompilerResult {

  private final List<OutputItem> myCompiledItems;
  private final List<? extends CompilerMessage> myCompilerMessages;

  public GroovyCompilerResult(@NotNull Collection<@NotNull OutputItem> items,
                              @NotNull Collection<? extends @NotNull CompilerMessage> messages) {
    myCompiledItems = new ArrayList<>(items);
    myCompilerMessages = new ArrayList<>(messages);
  }

  public @NotNull List<@NotNull OutputItem> getSuccessfullyCompiled() {
    return myCompiledItems;
  }

  public @NotNull List<? extends @NotNull CompilerMessage> getCompilerMessages() {
    return myCompilerMessages;
  }

  public boolean shouldRetry() {
    for (CompilerMessage message : myCompilerMessages) {
      String text = message.getMessageText();
      if (text.contains("java.lang.NoClassDefFoundError") ||
          text.contains("java.lang.TypeNotPresentException") ||
          text.contains("unable to resolve class")) {
        JpsGroovycRunner.LOG.debug("Resolve issue: " + message);
        return true;
      }
    }
    return false;
  }
}
