// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.Topic;

/**
 * @deprecated don't add new constants to this class and use replacements for existing ones.
 */
@Deprecated
public final class ProjectTopics {
  /**
   * @deprecated use {@link ModuleRootListener#TOPIC} instead
   */
  @Deprecated
  @Topic.ProjectLevel
  public static final Topic<ModuleRootListener> PROJECT_ROOTS = ModuleRootListener.TOPIC;

  /**
   * @deprecated use {@link ModuleListener#TOPIC} instead
   */
  @Deprecated 
  @Topic.ProjectLevel
  public static final Topic<ModuleListener> MODULES = ModuleListener.TOPIC;

  private ProjectTopics() {
  }
}