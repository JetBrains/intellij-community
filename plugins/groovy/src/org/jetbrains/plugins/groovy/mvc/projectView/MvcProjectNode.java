// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class MvcProjectNode extends AbstractProjectNode {
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcProjectNode(@NotNull Project project, final ViewSettings viewSettings, MvcToolWindowDescriptor descriptor) {
    super(project, project, viewSettings);
    myDescriptor = descriptor;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    List<Module> modules = MvcModuleStructureUtil.getAllModulesWithSupport(myProject, myDescriptor.getFramework());

    modules = myDescriptor.getFramework().reorderModulesForMvcView(modules);

    final ArrayList<AbstractTreeNode<?>> nodes = new ArrayList<>();
    for (Module module : modules) {
      nodes.add(new MvcModuleNode(module, getSettings(), myDescriptor));
    }
    return nodes;
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @NotNull
  @Override
  protected AbstractTreeNode<?> createModuleGroup(@NotNull final Module module) throws InstantiationException {
    return createTreeNode(MvcProjectNode.class, getProject(), module, getSettings());
  }

  @NotNull
  @Override
  protected AbstractTreeNode<?> createModuleGroupNode(@NotNull final ModuleGroup moduleGroup) throws InstantiationException {
    return createTreeNode(MvcProjectNode.class, getProject(), moduleGroup, getSettings());
  }

}