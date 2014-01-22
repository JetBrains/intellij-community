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

import java.util.Collection;
import java.util.Collections;

/**
 * @author Nadya Zabrodina
 */
public class VcsRootConfiguration {

  @NotNull private Collection<String> myMockRoots;
  @NotNull private Collection<String> myContentRoots;
  @NotNull private Collection<String> myRoots;
  @NotNull private Collection<String> myUnregErrors;
  @NotNull private Collection<String> myExtraErrors;


  public VcsRootConfiguration() {
    myMockRoots = Collections.emptyList();
    myRoots = Collections.emptyList();
    myContentRoots = Collections.emptyList();
    myUnregErrors = Collections.emptyList();
    myExtraErrors = Collections.emptyList();
  }

  public VcsRootConfiguration mock(@NotNull Collection<String> mockRoots) {
    myMockRoots = mockRoots;
    return this;
  }

  public VcsRootConfiguration roots(@NotNull Collection<String> roots) {
    myRoots = roots;
    return this;
  }

  public VcsRootConfiguration contentRoots(@NotNull Collection<String> contentRoots) {
    myContentRoots = contentRoots;
    return this;
  }

  public VcsRootConfiguration unregErrors(@NotNull Collection<String> unregErrors) {
    myUnregErrors = unregErrors;
    return this;
  }

  public VcsRootConfiguration extraErrors(@NotNull Collection<String> extraErrors) {
    myExtraErrors = extraErrors;
    return this;
  }

  @NotNull
  public Collection<String> getMockRoots() {
    return myMockRoots;
  }

  @NotNull
  public Collection<String> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public Collection<String> getRoots() {
    return myRoots;
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
