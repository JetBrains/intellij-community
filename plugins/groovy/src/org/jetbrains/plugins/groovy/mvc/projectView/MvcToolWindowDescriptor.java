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
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;

import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public abstract class MvcToolWindowDescriptor implements ToolWindowFactory, Condition<Project>, DumbAware {
  private final MvcFramework myFramework;

  public MvcToolWindowDescriptor(MvcFramework framework) {
    myFramework = framework;
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setAvailable(true, null);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(myFramework.getDisplayName());

    MvcProjectViewPane view = new MvcProjectViewPane(project, this);
    view.setup((ToolWindowEx)toolWindow);
  }

  @Override
  public boolean value(Project project) {
    return MvcModuleStructureUtil.hasModulesWithSupport(project, myFramework);
  }

  public String getToolWindowId() {
    return getToolWindowId(myFramework);
  }

  public static String getToolWindowId(final MvcFramework framework) {
    return framework.getFrameworkName() + " View";
  }

  public abstract void fillModuleChildren(List<AbstractTreeNode> result, final Module module, final ViewSettings viewSettings, VirtualFile root);

  public abstract Icon getModuleNodeIcon();

  @NotNull
  public abstract MvcProjectViewState getProjectViewState(Project project);

  @Nullable
  protected static PsiDirectory findDirectory(Project project, VirtualFile root, @NotNull String relativePath) {
    final VirtualFile file = root.findFileByRelativePath(relativePath);
    return file == null ? null : PsiManager.getInstance(project).findDirectory(file);
  }

}
