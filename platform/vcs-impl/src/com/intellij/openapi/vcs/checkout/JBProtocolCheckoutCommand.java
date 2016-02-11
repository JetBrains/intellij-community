/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.CheckoutProviderEx;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class JBProtocolCheckoutCommand extends JBProtocolCommand {
  private static final String REPOSITORY_NAME_KEY = "checkout.repo";

  public JBProtocolCheckoutCommand() {
    super("checkout");
  }


  @Override
  public void perform(String vcsId, Map<String, String> parameters) {
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
          providerEx.doCheckout(project, listener, repository);
          break;
        }
      }
    }
  }
}
