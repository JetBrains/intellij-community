// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.CheckoutProviderEx;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.ui.AppIcon;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class JBProtocolCheckoutCommand extends JBProtocolCommand {
  private static final String REPOSITORY_NAME_KEY = "checkout.repo";

  JBProtocolCheckoutCommand() {
    super("checkout");
  }


  @Override
  public void perform(String vcsId, @NotNull Map<String, String> parameters) {
    String repository = parameters.get(REPOSITORY_NAME_KEY);

    if (StringUtil.isEmpty(repository)) {
      return;
    }

    for (CheckoutProvider provider : CheckoutProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (provider instanceof CheckoutProviderEx) {
        CheckoutProviderEx providerEx = (CheckoutProviderEx)provider;
        if (providerEx.getVcsId().equals(vcsId)) {
          Project project = ProjectManager.getInstance().getDefaultProject();
          CheckoutProvider.Listener listener = ProjectLevelVcsManager.getInstance(project).getCompositeCheckoutListener();
          AppIcon.getInstance().requestAttention(null, true);
          providerEx.doCheckout(project, listener, repository);
          break;
        }
      }
    }
  }
}
