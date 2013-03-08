/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.explorer;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
* @author VISTALL
* @date 14:08/08.03.13
*/
final class AntModuleInfoNodeDescriptor extends AntNodeDescriptor {
  private Module myModule;

  public AntModuleInfoNodeDescriptor(Project project, NodeDescriptor parentDescriptor, Module module) {
    super(project, parentDescriptor);
    myModule = module;
    myName = module.getName();
    myClosedIcon = AllIcons.Actions.Module;
  }

  @Override
  public boolean isAutoExpand() {
    return false;
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public Object getElement() {
    return myModule;
  }

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    component.append(toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    component.setIcon(getIcon());
  }
}
