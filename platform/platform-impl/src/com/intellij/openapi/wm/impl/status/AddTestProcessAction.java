// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("HardCodedStringLiteral")
public class AddTestProcessAction extends AnAction implements DumbAware {
  public AddTestProcessAction() {
    super("Add Test Process");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    new Task.Backgroundable(project, "Test process", true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Welcome!");

        for (int each = 0; each < 1000; each++) {
          indicator.setText("Found: " + each / 20 + 1);
          if (each / 10.0 == Math.round(each / 10.0)) {
            indicator.setText(null);
          }
          indicator.setFraction(each / 1000.0);

          TimeoutUtil.sleep(100);

          indicator.checkCanceled();
          indicator.setText2("Bla bla bla");
        }
      }
    }.queue();
  }
}
