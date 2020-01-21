// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.Topic;

public class ProjectTopics {
  public static final Topic<ModuleRootListener> PROJECT_ROOTS = new Topic<>("project root changes", ModuleRootListener.class);
  public static final Topic<ModuleListener> MODULES = new Topic<>("modules added or removed from project", ModuleListener.class);

  private ProjectTopics() {
  }
}