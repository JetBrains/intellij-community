// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
@SuppressWarnings("HardCodedStringLiteral")
public class AddManyTestProcesses extends DumbAwareAction {
  public AddManyTestProcesses() {
    super("Add Many Test Processes");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    for (int i = 0; i < 100; i++) {
      final int finalI = i;
      new Task.Backgroundable(project, "Test process", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          for (int j = 0; j < 10000; j++) {
            TimeoutUtil.sleep(1);
            indicator.setText("foo " + j);
            if (finalI % 10 == 0) {
              indicator.setText2(j + " foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo foo");
            }
          }
        }
      }.queue();
    }
  }
}
