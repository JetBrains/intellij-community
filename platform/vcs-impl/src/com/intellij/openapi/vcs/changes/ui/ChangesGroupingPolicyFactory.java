// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;

public abstract class ChangesGroupingPolicyFactory {
  public static final ExtensionPointName<ChangesGroupingPolicyFactoryEPBean> EP_NAME = ExtensionPointName.create("com.intellij.changesGroupingPolicy");

  @NotNull
  public abstract ChangesGroupingPolicy createGroupingPolicy(@NotNull Project project, @NotNull DefaultTreeModel model);
}
