// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class TooManyProjectLeakedException extends RuntimeException {
  private final Iterable<? extends Project> myLeakedProjects;

  public TooManyProjectLeakedException(@NotNull Iterable<? extends Project> leakedProjects) {
    myLeakedProjects = leakedProjects;
  }

  public @NotNull Iterable<? extends Project> getLeakedProjects() {
    return myLeakedProjects;
  }
}
