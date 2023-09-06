// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.Topic;

public final class ProjectTopics {
  /**
   * @deprecated use {@link ModuleRootListener#TOPIC} instead
   */
  @Deprecated
  @Topic.ProjectLevel
  public static final Topic<ModuleRootListener> PROJECT_ROOTS = ModuleRootListener.TOPIC;

  /**
   * Modules added, removed, or renamed in project.
   */
  @Topic.ProjectLevel
  public static final Topic<ModuleListener> MODULES = new Topic<>(ModuleListener.class, Topic.BroadcastDirection.NONE);

  private ProjectTopics() {
  }
}