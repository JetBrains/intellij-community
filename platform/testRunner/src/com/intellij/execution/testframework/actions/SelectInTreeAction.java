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

package com.intellij.execution.testframework.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class SelectInTreeAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final TestContext context = TestContext.from(e);
    if (!shouldBeEnabled(context))
      return;
    context.getModel().getTreeBuilder().select(context.getSelection());
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(shouldBeEnabled(TestContext.from(e)));
  }

  private static boolean shouldBeEnabled(final TestContext context) {
    if (context == null || !context.hasSelection())
      return false;
    return context.treeContainsSelection();
  }
}
