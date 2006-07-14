package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildModelBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

final class AntBuildFileNodeDescriptor extends AntNodeDescriptor {
  private static final Icon ICON = IconLoader.getIcon("/ant/build.png");

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
    CompositeAppearance oldAppearence = myAppearance;
    myAppearance = new CompositeAppearance();
    myAppearance.getEnding().addText(myBuildFile.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    final AntBuildModelBase buildModel = myBuildFile.getModelIfRegistered();
    if (buildModel != null) {
      AntTargetNodeDescriptor.addShortcutText(buildModel.getDefaultTargetActionId(), myAppearance);
    }
    myOpenIcon = myClosedIcon = ICON;
    myName = myBuildFile.getPresentableName();
    return !Comparing.equal(myAppearance, oldAppearence);
  }

  public void customize(SimpleColoredComponent component) {
    if (myAppearance != null) myAppearance.customize(component);
    else super.customize(component);
  }

  public boolean isAutoExpand() {
    return myBuildFile.shouldExpand();
  }
}
