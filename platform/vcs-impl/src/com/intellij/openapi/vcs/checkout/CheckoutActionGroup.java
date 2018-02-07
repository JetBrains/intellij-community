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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ComputableActionGroup;
import com.intellij.openapi.vcs.CheckoutProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CheckoutActionGroup extends ComputableActionGroup.Simple {
  protected final String myIdPrefix;

  @SuppressWarnings("unused")
  public CheckoutActionGroup() {
    this("Vcs.Checkout");
  }

  public CheckoutActionGroup(String idPrefix) {
    myIdPrefix = idPrefix;
  }

  @NotNull
  @Override
  protected AnAction[] computeChildren(@NotNull ActionManager manager) {
    return getActions();
  }

  @NotNull
  public AnAction[] getActions() {
    CheckoutProvider[] providers = CheckoutProvider.EXTENSION_POINT_NAME.getExtensions();
    if (providers.length == 0) {
      return EMPTY_ARRAY;
    }

    Arrays.sort(providers, new CheckoutProvider.CheckoutProviderComparator());
    AnAction[] children = new AnAction[providers.length];
    for (int i = 0; i < providers.length; i++) {
      CheckoutProvider provider = providers[i];
      children[i] = createAction(provider);
    }
    return children;
  }

  @NotNull
  protected AnAction createAction(CheckoutProvider provider) {
    return new CheckoutAction(provider, myIdPrefix);
  }
}
