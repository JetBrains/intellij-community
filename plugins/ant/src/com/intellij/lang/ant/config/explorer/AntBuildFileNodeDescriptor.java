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
package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildModelBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

final class AntBuildFileNodeDescriptor extends AntNodeDescriptor {

  private final AntBuildFileBase myBuildFile;
  private CompositeAppearance myAppearance;

  public AntBuildFileNodeDescriptor(Project project, NodeDescriptor parentDescriptor, AntBuildFileBase buildFile) {
    super(project, parentDescriptor);
    myBuildFile = buildFile;
  }

  public Object getElement() {
    return myBuildFile;
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }

  public boolean update() {
    CompositeAppearance oldAppearance = myAppearance;
    myAppearance = new CompositeAppearance();
    myAppearance.getEnding().addText(myBuildFile.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    final AntBuildModelBase buildModel = myBuildFile.getModelIfRegistered();
    if (buildModel != null) {
      AntTargetNodeDescriptor.addShortcutText(buildModel.getDefaultTargetActionId(), myAppearance);
    }
    myAppearance.setIcon(AntIcons.Build);
    myName = myBuildFile.getPresentableName();
    return !Comparing.equal(myAppearance, oldAppearance);
  }

  public void customize(@NotNull SimpleColoredComponent component) {
    if (myAppearance != null) {
      myAppearance.customize(component);
    }
    else {
      super.customize(component);
    }
  }

  @Override
  public void customize(@NotNull final HtmlListCellRenderer renderer) {
    if (myAppearance != null) {
      myAppearance.customize(renderer);
    }
    else {
      super.customize(renderer);
    }
  }

  public boolean isAutoExpand() {
    return myBuildFile.shouldExpand();
  }
}
