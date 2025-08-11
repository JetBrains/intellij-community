// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;

public abstract class ChangesGroupingPolicyFactory {
  public static final ExtensionPointName<ChangesGroupingPolicyFactoryEPBean> EP_NAME = ExtensionPointName.create("com.intellij.changesGroupingPolicy");

  public abstract @NotNull ChangesGroupingPolicy createGroupingPolicy(@NotNull Project project, @NotNull DefaultTreeModel model);
}
