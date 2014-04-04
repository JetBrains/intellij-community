package com.jetbrains.javascript.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ObjectPropertyData {
  @NotNull
  List<? extends ObjectProperty> getProperties();

  @NotNull
  List<? extends Variable> getInternalProperties();

  @Nullable
  Variable getProperty(String name);

  int getCacheState();
}
