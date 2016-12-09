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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public class SwitchableDelegateAction extends AnAction {
  @NotNull private final AnAction myMainDelegate;
  @NotNull private final AnAction myAlternateDelegate;
  @NotNull private final String myRegistryKey;

  public SwitchableDelegateAction(@NotNull AnAction mainAction, @NotNull AnAction alternateAction, @NotNull String registryKey) {
    myMainDelegate = mainAction;
    myAlternateDelegate = alternateAction;
    myRegistryKey = registryKey;

    copyFrom(mainAction);
    setEnabledInModalContext(mainAction.isEnabledInModalContext());
  }

  @NotNull
  private AnAction getDelegateAction() {
    if (Registry.is(myRegistryKey)) {
      return myMainDelegate;
    }
    return myAlternateDelegate;
  }

  @Override
  public void update(final AnActionEvent e) {
    getDelegateAction().update(e);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    getDelegateAction().actionPerformed(e);
  }

  @Override
  public boolean isDumbAware() {
    return getDelegateAction().isDumbAware();
  }

  @Override
  public boolean isTransparentUpdate() {
    return getDelegateAction().isTransparentUpdate();
  }

  @Override
  public boolean isInInjectedContext() {
    return getDelegateAction().isInInjectedContext();
  }
}
