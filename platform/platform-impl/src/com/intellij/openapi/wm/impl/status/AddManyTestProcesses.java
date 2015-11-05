/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class AddManyTestProcesses extends DumbAwareAction {
  public AddManyTestProcesses() {
    super("Add Many Test Processes");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    for (int i = 0; i < 100; i++) {
      final int finalI = i;
      new Task.Backgroundable(project, "Test Process", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
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
