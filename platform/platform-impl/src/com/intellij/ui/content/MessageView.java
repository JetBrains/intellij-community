// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface MessageView  {
  ContentManager getContentManager();

  void runWhenInitialized(@NotNull Runnable runnable);

  static MessageView getInstance(@NotNull Project project) {
    return project.getService(MessageView.class);
  }

  /**
   * @deprecated use {@link MessageView#getInstance(Project)} instead
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {
    private SERVICE() {
    }

    public static MessageView getInstance(@NotNull Project project) {
      return MessageView.getInstance(project);
    }
  }
}
