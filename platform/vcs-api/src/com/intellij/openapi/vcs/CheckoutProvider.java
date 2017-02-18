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
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Comparator;

import static com.intellij.ui.GuiUtils.getTextWithoutMnemonicEscaping;

/**
 * Implement this interface and register it as extension to checkoutProvider extension point in order to provide checkout
 */
public interface CheckoutProvider {
  @NonNls ExtensionPointName<CheckoutProvider> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.checkoutProvider");

  /**
   * @param project current project or default project if no project is open.
   */
  void doCheckout(@NotNull final Project project, @Nullable Listener listener);
  @NonNls String getVcsName();

  interface Listener {
    void directoryCheckedOut(File directory, VcsKey vcs);
    void checkoutCompleted();
  }

  class CheckoutProviderComparator implements Comparator<CheckoutProvider> {
    public int compare(@NotNull final CheckoutProvider o1, @NotNull final CheckoutProvider o2) {
      return getTextWithoutMnemonicEscaping(o1.getVcsName()).compareTo(getTextWithoutMnemonicEscaping(o2.getVcsName()));
    }
  }
}
