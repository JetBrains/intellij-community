// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

public class CheckoutAction extends AnAction implements DumbAware, ActionIdProvider {
  private final CheckoutProvider myProvider;
  private final String myIdPrefix;

  public CheckoutAction(CheckoutProvider provider, String idPrefix) {
    super(provider.getVcsName());
    myProvider = provider;
    myIdPrefix = idPrefix;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    project = (project == null) ? ProjectManager.getInstance().getDefaultProject() : project;
    myProvider.doCheckout(project, getListener(project));
  }

  protected CheckoutProvider.Listener getListener(Project project) {
    return ProjectLevelVcsManager.getInstance(project).getCompositeCheckoutListener();
  }

  @Override
  public String getId() {
    return myIdPrefix + "." + myProvider.getVcsName().replaceAll("_", "");
  }
}
