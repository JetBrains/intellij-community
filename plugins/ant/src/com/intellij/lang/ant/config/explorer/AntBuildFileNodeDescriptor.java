// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildModelBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

final class AntBuildFileNodeDescriptor extends AntNodeDescriptor {

  private final AntBuildFileBase myBuildFile;
  private CompositeAppearance myAppearance;

  AntBuildFileNodeDescriptor(Project project, NodeDescriptor parentDescriptor, AntBuildFileBase buildFile) {
    super(project, parentDescriptor);
    myBuildFile = buildFile;
  }

  @Override
  public Object getElement() {
    return myBuildFile;
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }

  @Override
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

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    if (myAppearance != null) {
      myAppearance.customize(component);
    }
    else {
      super.customize(component);
    }
  }
}