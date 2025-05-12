// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class VcsRootConfiguration {
  private @NotNull Collection<String> myVcsRoots;
  private @NotNull Collection<String> myContentRoots;
  private @NotNull Collection<String> myMappings;
  private @NotNull Collection<String> myUnregErrors;
  private @NotNull Collection<String> myExtraErrors;

  public VcsRootConfiguration() {
    myVcsRoots = Collections.emptyList();
    myMappings = Collections.emptyList();
    myContentRoots = Collections.emptyList();
    myUnregErrors = Collections.emptyList();
    myExtraErrors = Collections.emptyList();
  }

  public @NotNull VcsRootConfiguration vcsRoots(String @NotNull ... vcsRoots) {
    myVcsRoots = Arrays.asList(vcsRoots);
    return this;
  }

  public @NotNull VcsRootConfiguration mappings(String @NotNull ... mappings) {
    myMappings = Arrays.asList(mappings);
    return this;
  }

  public @NotNull VcsRootConfiguration contentRoots(String @NotNull ... contentRoots) {
    myContentRoots = Arrays.asList(contentRoots);
    return this;
  }

  public @NotNull VcsRootConfiguration unregErrors(String @NotNull ... unregErrors) {
    myUnregErrors = Arrays.asList(unregErrors);
    return this;
  }

  public @NotNull VcsRootConfiguration extraErrors(String @NotNull ... extraErrors) {
    myExtraErrors = Arrays.asList(extraErrors);
    return this;
  }

  public @NotNull Collection<String> getVcsRoots() {
    return myVcsRoots;
  }

  public @NotNull Collection<String> getContentRoots() {
    Set<String> result = new HashSet<>(myContentRoots);
    result.addAll(myVcsRoots);
    return result;
  }

  public @NotNull Collection<String> getVcsMappings() {
    return myMappings;
  }

  public @NotNull Collection<String> getUnregErrors() {
    return myUnregErrors;
  }

  public @NotNull Collection<String> getExtraErrors() {
    return myExtraErrors;
  }
}
