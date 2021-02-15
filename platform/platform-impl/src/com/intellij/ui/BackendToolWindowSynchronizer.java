// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.viewModel.definition.ToolWindowViewModelContent;
import com.intellij.ui.viewModel.definition.ToolWindowViewModelDescription;
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

//override this class and work with the view model in your protocol-specific code
public abstract class BackendToolWindowSynchronizer {

  private static final Logger myLogger = Logger.getInstance(BackendToolWindowSynchronizer.class);
  private final Project myProject;

  public BackendToolWindowSynchronizer(Project project) {
    myProject = project;

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    for (String id : toolWindowManager.getToolWindowIds()) {
      passRegisteredToolWindowDescription(id, toolWindowManager, project);
    }

    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener(){
      @Override
      public void toolWindowsRegistered(@NotNull List<String> ids,
                                        @NotNull ToolWindowManager toolWindowManager) {
        for (String id : ids) {
          passRegisteredToolWindowDescription(id, toolWindowManager, project);
        }
      }

      //todo tool window unregistered
    });
  }

  public void passRegisteredToolWindowDescription(String id,
                                                  @NotNull ToolWindowManager toolWindowManager,
                                                  Project project) {
    final ToolWindowViewModelExtractor[] extractors = ToolWindowViewModelExtractor.EP_NAME.getExtensions();
    for (ToolWindowViewModelExtractor extractor : extractors) {

      if (!extractor.isApplicable(id)) {
        continue;
      }

      ToolWindow window = toolWindowManager.getToolWindow(id);
      myLogger.assertTrue(window != null,  String.format("Tool window with id %s could not be found", id));
      ToolWindowViewModelDescription description = extractor.extractDescription(window);

      passToolWindowDescription(description, project);
    }
  }

  @Nullable
  protected ToolWindowViewModelContent getContent(String id) {
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(id);

    final ToolWindowViewModelExtractor[] extractors = ToolWindowViewModelExtractor.EP_NAME.getExtensions();
    for (ToolWindowViewModelExtractor extractor : extractors) {

      if (!extractor.isApplicable(id)) {
        continue;
      }

      return extractor.extractViewModel(window, myProject);
    }

    return null;
  }

  public abstract void passToolWindowDescription(ToolWindowViewModelDescription toolWindowViewModel,
                                                 Project project);

}