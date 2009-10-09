package com.intellij.openapi.components;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface TrackingPathMacroSubstitutor extends PathMacroSubstitutor {
  Collection<String> getUnknownMacros(@Nullable String componentName);
  Collection<String> getComponents(final Collection<String> macros);
  void addUnknownMacros(String componentName, Collection<String> unknownMacros);
  void invalidateUnknownMacros(Set<String> macros);
  void reset();
}
