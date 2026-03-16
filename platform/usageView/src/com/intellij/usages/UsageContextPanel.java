// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;

/**
 * Component showing additional information for the selected usage in the Usage View.
 * Examples: Preview, Data flow, Call hierarchy
 */
public interface UsageContextPanel extends Disposable {
  // usage selection changes, panel should update its view for the newly select usages
  void updateLayout(@NotNull Project project, @Nullable("null means there are no usages to show") List<? extends UsageInfo> infos);

  @ApiStatus.Internal
  default void updateLayout(@NotNull Project project, @Nullable("null means there are no usages to show") List<? extends UsageInfo> infos,
                    boolean severalFilesSelected) {
    updateLayout(project, infos);
  }

  default void updateLayout(@NotNull Project project, @NotNull List<? extends UsageInfo> infos, @NotNull UsageView usageView) {
    updateLayout(project, infos, false);
  }

  /**
   * @deprecated Use {@link #updateLayout(Project, List)}
   */
  @Deprecated
  void updateLayout(@Nullable("null means there are no usages to show") List<? extends UsageInfo> infos);

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
