/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class MvcProjectNode extends AbstractProjectNode {
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcProjectNode(final Project project, final ViewSettings viewSettings, MvcToolWindowDescriptor descriptor) {
    super(project, project, viewSettings);
    myDescriptor = descriptor;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<Module> modules = MvcModuleStructureUtil.getAllModulesWithSupport(myProject, myDescriptor.getFramework());

    modules = myDescriptor.getFramework().reorderModulesForMvcView(modules);
    
    final ArrayList<AbstractTreeNode> nodes = new ArrayList<>();
    for (Module module : modules) {
      nodes.add(new MvcModuleNode(module, getSettings(), myDescriptor));
    }
    return nodes;
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @Override
  protected AbstractTreeNode createModuleGroup(final Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return createTreeNode(MvcProjectNode.class, getProject(), module, getSettings());
  }

  @Override
  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return createTreeNode(MvcProjectNode.class, getProject(), moduleGroup, getSettings());
  }

}