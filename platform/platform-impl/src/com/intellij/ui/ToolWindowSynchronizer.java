// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.viewModel.definition.ToolWindowViewModel;
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

//override this class and work with the view model in your protocol-specific code
public abstract class ToolWindowSynchronizer {

  private static final Logger myLogger = Logger.getInstance(ToolWindowSynchronizer.class);

  public ToolWindowSynchronizer(Project project) {
    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener(){
      @Override
      public void toolWindowsRegistered(@NotNull List<String> ids,
                                        @NotNull ToolWindowManager toolWindowManager) {
        for (String id : ids) {
          processToolWindow(id, toolWindowManager);
        }
      }
    });
  }

  public void processToolWindow(String id, @NotNull ToolWindowManager toolWindowManager) {
    final ToolWindowViewModelExtractor[] extractors = ToolWindowViewModelExtractor.EP_NAME.getExtensions();
    for (ToolWindowViewModelExtractor extractor : extractors) {

      if (!extractor.isApplicable(id)) {
        continue;
      }

      ToolWindow window = toolWindowManager.getToolWindow(id);
      myLogger.assertTrue(window != null,  String.format("Tool window with id %s could not be found", id));
      ToolWindowViewModel toolWindowViewModel = extractor.extractViewModel(window);

      passViewModel(toolWindowViewModel);
    }
  }

  abstract void passViewModel(ToolWindowViewModel toolWindowViewModel);
}