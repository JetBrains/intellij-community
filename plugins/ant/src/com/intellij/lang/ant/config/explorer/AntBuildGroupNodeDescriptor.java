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
import com.intellij.lang.ant.config.AntBuildFileGroup;
import com.intellij.openapi.project.Project;

/**
 * @author VISTALL
 * @since 11:52/09.03.13
 */
public class AntBuildGroupNodeDescriptor extends AntNodeDescriptor {
  private final AntBuildFileGroup myGroup;

  public AntBuildGroupNodeDescriptor(Project project, NodeDescriptor parentDescriptor, AntBuildFileGroup group) {
    super(project, parentDescriptor);
    myGroup = group;
    myName = myGroup.getName();
    setIcon(AllIcons.Ant.BuildGroup);
  }

  @Override
  public boolean isAutoExpand() {
    return true;
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public AntBuildFileGroup getElement() {
    return myGroup;
  }
}
