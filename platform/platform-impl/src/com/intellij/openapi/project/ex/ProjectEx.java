// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ProjectEx extends Project {
  interface ProjectSaved {
    Topic<ProjectSaved> TOPIC = Topic.create("SaveProjectTopic", ProjectSaved.class, Topic.BroadcastDirection.NONE);

    void saved(@NotNull final Project project);
  }

  void init();

  void setProjectName(@NotNull String name);

  void save(boolean isForceSavingAllSettings);
}
