package com.intellij.openapi.components;

import java.util.Set;

public interface TrackingPathMacroSubstitutor extends PathMacroSubstitutor {
  Set<String> getUsedMacros();
  void reset();
  void reset(Set<String> usedMacros);
}
