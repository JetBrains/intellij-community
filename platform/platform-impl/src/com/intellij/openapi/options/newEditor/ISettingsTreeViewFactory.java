// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.ConfigurableGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface ISettingsTreeViewFactory {
  @NotNull
  SettingsTreeView createTreeView(@NotNull SettingsFilter filter, @NotNull List<? extends ConfigurableGroup> groups);
}
