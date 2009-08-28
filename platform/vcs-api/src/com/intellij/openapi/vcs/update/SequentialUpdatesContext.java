package com.intellij.openapi.vcs.update;

import org.jetbrains.annotations.NotNull;

public interface SequentialUpdatesContext {
  @NotNull
  String getMessageWhenInterruptedBeforeStart();
}
