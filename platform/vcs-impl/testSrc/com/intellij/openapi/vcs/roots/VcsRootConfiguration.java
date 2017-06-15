/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.roots;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

class VcsRootConfiguration {

  @NotNull private Collection<String> myVcsRoots;
  @NotNull private Collection<String> myContentRoots;
  @NotNull private Collection<String> myMappings;
  @NotNull private Collection<String> myUnregErrors;
  @NotNull private Collection<String> myExtraErrors;


  public VcsRootConfiguration() {
    myVcsRoots = Collections.emptyList();
    myMappings = Collections.emptyList();
    myContentRoots = Collections.emptyList();
    myUnregErrors = Collections.emptyList();
    myExtraErrors = Collections.emptyList();
  }

  @NotNull
  public VcsRootConfiguration vcsRoots(@NotNull String... vcsRoots) {
    myVcsRoots = Arrays.asList(vcsRoots);
    return this;
  }

  @NotNull
  public VcsRootConfiguration mappings(@NotNull String... mappings) {
    myMappings = Arrays.asList(mappings);
    return this;
  }

  @NotNull
  public VcsRootConfiguration contentRoots(@NotNull String... contentRoots) {
    myContentRoots = Arrays.asList(contentRoots);
    return this;
  }

  @NotNull
  public VcsRootConfiguration unregErrors(@NotNull String... unregErrors) {
    myUnregErrors = Arrays.asList(unregErrors);
    return this;
  }

  @NotNull
  public VcsRootConfiguration extraErrors(@NotNull String... extraErrors) {
    myExtraErrors = Arrays.asList(extraErrors);
    return this;
  }

  @NotNull
  public Collection<String> getVcsRoots() {
    return myVcsRoots;
  }

  @NotNull
  public Collection<String> getContentRoots() {
    return myContentRoots;
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
