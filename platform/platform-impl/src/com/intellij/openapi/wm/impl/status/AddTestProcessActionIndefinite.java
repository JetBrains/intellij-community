/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessActionIndefinite extends AnAction implements DumbAware {
  public AddTestProcessActionIndefinite() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    new Task.Backgroundable(project, "Test", true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Thread.currentThread().sleep(6000);

          countTo(900, new AddTestProcessActionIndefinite.Count() {
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
              }
              Thread.currentThread().sleep(10);
              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
            }
          });
          indicator.stop();
        }
        catch (Exception e1) {
          indicator.stop();
          return;
        }
      }
    }.queue();
  }

  private void countTo(int top, AddTestProcessActionIndefinite.Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}
