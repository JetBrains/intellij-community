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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;

public abstract class ChangesGroupingPolicyFactory {
  public static final ExtensionPointName<KeyedFactoryEPBean> EP_NAME = ExtensionPointName.create("com.intellij.changesGroupingPolicy");

  public static ChangesGroupingPolicyFactory getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ChangesGroupingPolicyFactory.class);
  }

  @NotNull
  public abstract ChangesGroupingPolicy createGroupingPolicy(@NotNull DefaultTreeModel model);
}
