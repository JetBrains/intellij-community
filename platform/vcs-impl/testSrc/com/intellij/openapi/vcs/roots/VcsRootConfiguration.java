// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class VcsRootConfiguration {
  @NotNull private Collection<String> myVcsRoots;
  @NotNull private Collection<String> myContentRoots;
  @NotNull private Collection<String> myMappings;
  @NotNull private Collection<String> myUnregErrors;
  @NotNull private Collection<String> myExtraErrors;

  VcsRootConfiguration() {
    myVcsRoots = Collections.emptyList();
    myMappings = Collections.emptyList();
    myContentRoots = Collections.emptyList();
    myUnregErrors = Collections.emptyList();
    myExtraErrors = Collections.emptyList();
  }

  @NotNull
  public VcsRootConfiguration vcsRoots(String @NotNull ... vcsRoots) {
    myVcsRoots = Arrays.asList(vcsRoots);
    return this;
  }

  @NotNull
  public VcsRootConfiguration mappings(String @NotNull ... mappings) {
    myMappings = Arrays.asList(mappings);
    return this;
  }

  @NotNull
  public VcsRootConfiguration contentRoots(String @NotNull ... contentRoots) {
    myContentRoots = Arrays.asList(contentRoots);
    return this;
  }

  @NotNull
  public VcsRootConfiguration unregErrors(String @NotNull ... unregErrors) {
    myUnregErrors = Arrays.asList(unregErrors);
    return this;
  }

  @NotNull
  public VcsRootConfiguration extraErrors(String @NotNull ... extraErrors) {
    myExtraErrors = Arrays.asList(extraErrors);
    return this;
  }

  @NotNull
  public Collection<String> getVcsRoots() {
    return myVcsRoots;
  }

  @NotNull
  public Collection<String> getContentRoots() {
    Set<String> result = new HashSet<>(myContentRoots);
    result.addAll(myVcsRoots);
    return result;
  }

  @NotNull
  public Collection<String> getVcsMappings() {
    return myMappings;
  }

  @NotNull
  public Collection<String> getUnregErrors() {
    return myUnregErrors;
  }

  @NotNull
  public Collection<String> getExtraErrors() {
    return myExtraErrors;
  }
}
