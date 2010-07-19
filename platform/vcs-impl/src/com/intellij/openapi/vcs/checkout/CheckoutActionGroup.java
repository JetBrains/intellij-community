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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.CheckoutProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

public class CheckoutActionGroup extends ActionGroup implements DumbAware {

  private AnAction[] myChildren;

  public void update(AnActionEvent e) {
    super.update(e);
    final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
    if (providers.length == 0) {
      e.getPresentation().setVisible(false);
    }
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myChildren == null) {
      final CheckoutProvider[] providers = Extensions.getExtensions(CheckoutProvider.EXTENSION_POINT_NAME);
      Arrays.sort(providers, new Comparator<CheckoutProvider>() {
        public int compare(final CheckoutProvider o1, final CheckoutProvider o2) {
          // not strict but will do
          return o1.getVcsName().compareTo(o2.getVcsName());
        }
      });
      myChildren = new AnAction[providers.length];
      for (int i = 0; i < providers.length; i++) {
        CheckoutProvider provider = providers[i];
        myChildren[i] = new CheckoutAction(provider);
      }
    }
    return myChildren;
  }
}
