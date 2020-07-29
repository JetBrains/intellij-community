// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface MessageView  {
  ContentManager getContentManager();

  void runWhenInitialized(@NotNull Runnable runnable);

  final class SERVICE {
    private SERVICE() {
    }

    public static MessageView getInstance(@NotNull Project project) {
      return project.getService(MessageView.class);
    }
  }
}
