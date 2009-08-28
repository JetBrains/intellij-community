package com.intellij.ui.content;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

public interface MessageView  {

  ContentManager getContentManager();

  void runWhenInitialized(final Runnable runnable);

  class SERVICE {
    private SERVICE() {
    }

    public static MessageView getInstance(Project project) {
      return ServiceManager.getService(project, MessageView.class);
    }
  }
}
