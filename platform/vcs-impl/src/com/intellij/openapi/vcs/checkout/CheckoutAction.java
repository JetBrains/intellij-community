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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

public class CheckoutAction extends AnAction implements DumbAware {
  private final CheckoutProvider myProvider;

  public CheckoutAction(final CheckoutProvider provider) {
    super(provider.getVcsName());
    myProvider = provider;
  }

  public void actionPerformed(final AnActionEvent e) {
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      @Override
      public void run() {
        Project project = e.getData(CommonDataKeys.PROJECT);
        project = (project == null) ? ProjectManager.getInstance().getDefaultProject() : project;
        myProvider.doCheckout(project, getListener(project));
      }
    });
  }

  protected CheckoutProvider.Listener getListener(Project project) {
    return ProjectLevelVcsManager.getInstance(project).getCompositeCheckoutListener();
  }
}
