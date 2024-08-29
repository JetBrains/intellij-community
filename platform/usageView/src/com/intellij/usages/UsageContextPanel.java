/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Component showing additional information for the selected usage in the Usage View.
 * Examples: Preview, Data flow, Call hierarchy
 */
public interface UsageContextPanel extends Disposable {
  // usage selection changes, panel should update its view for the newly select usages
  void updateLayout(@NotNull Project project, @Nullable("null means there are no usages to show") List<? extends UsageInfo> infos);

  default void updateLayout(@NotNull Project project, @NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    updateLayout(project, infos);
  }

  /**
   * @deprecated Use {@link #updateLayout(Project, List)}
   */
  @Deprecated
  void updateLayout(@Nullable("null means there are no usages to show") List<? extends UsageInfo> infos);

  /**
   * @deprecated Use {@link #updateLayout(Project, List, UsageView)}
   */
  @Deprecated
  default void updateLayout(@NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    updateLayout(infos);
  }

  @NotNull
  JComponent createComponent();

  interface Provider {
    @ApiStatus.Internal
    ExtensionPointName<Provider> EP_NAME = ExtensionPointName.create("com.intellij.usageContextPanelProvider");

    @NotNull
    UsageContextPanel create(@NotNull UsageView usageView);

    /**
     * E.g. Call hierarchy is not available for variable usages
     */
    boolean isAvailableFor(@NotNull UsageView usageView);

    @NotNull
    @TabTitle
    String getTabTitle();
  }
}
