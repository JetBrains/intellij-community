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
package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.MetaTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AntExplorerTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.explorer.AntExplorerTreeStructure");
  private final Project myProject;
  private final Object myRoot = new Object();
  private boolean myFilteredTargets = false;
  private AntTreeView myTreeView;

  public AntExplorerTreeStructure(final Project project) {
    myProject = project;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final Object element) {
    return true;
  }

  @Override
  @NotNull
  public AntNodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myRoot) {
      return new RootNodeDescriptor(myProject, parentDescriptor);
    }
    
    if (element instanceof String) {
      return new TextInfoNodeDescriptor(myProject, parentDescriptor, (String)element);
    }

    if(element instanceof Module) {
      return new AntModuleInfoNodeDescriptor(myProject, parentDescriptor, (Module) element);
    }
    
    if (element instanceof AntBuildFileBase) {
      return new AntBuildFileNodeDescriptor(myProject, parentDescriptor, (AntBuildFileBase)element);
    }
    
    if (element instanceof AntBuildTargetBase) {
      return new AntTargetNodeDescriptor(myProject, parentDescriptor, (AntBuildTargetBase)element);
    }

    if(element instanceof AntBuildFileGroup) {
      return new AntBuildGroupNodeDescriptor(myProject, parentDescriptor, (AntBuildFileGroup) element);
    }
    
    LOG.error("Unknown element for this tree structure " + element);
    return null;
  }

  @Override
  public Object[] getChildElements(Object element) {
    final AntConfiguration configuration = AntConfiguration.getInstance(myProject);
    if (element == myRoot) {
      if (!configuration.isInitialized()) {
        return new Object[] {AntBundle.message("loading.ant.config.progress")};
      }
      return myTreeView.getRootChildren(myProject);
    }

    return myTreeView.getChildren(myProject, element, myFilteredTargets);
  }

  @Override
  @Nullable
  public Object getParentElement(Object element) {
    if (element instanceof AntBuildTarget) {
      if (element instanceof MetaTarget) {
        return ((MetaTarget)element).getBuildFile();
      }
      return ((AntBuildTarget)element).getModel().getBuildFile();
    }
    
    if (element instanceof AntBuildFile) {
      return myRoot;
    }
    
    return null;
  }

  @Override
  public void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  @NotNull
  @Override
  public ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  public void setFilteredTargets(boolean value) {
    myFilteredTargets = value;
  }

  public void setModuleGrouping(boolean value) {
    myTreeView = value ? AntTreeView.MODULE_GROUPING : AntTreeView.NO_GROUPING;
  }

  private final class RootNodeDescriptor extends AntNodeDescriptor {
    public RootNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
      super(project, parentDescriptor);
    }

    @Override
    public boolean isAutoExpand() {
      return true;
    }

    @Override
    public Object getElement() {
      return myRoot;
    }

    @Override
    public boolean update() {
      myName = "";
      return false;
    }
  }

  private static final class TextInfoNodeDescriptor extends AntNodeDescriptor {
    public TextInfoNodeDescriptor(Project project, NodeDescriptor parentDescriptor, String text) {
      super(project, parentDescriptor);
      myName = text;
      myColor = JBColor.blue;
    }

    @Override
    public Object getElement() {
      return myName;
    }

    @Override
    public boolean update() {
      return true;
    }

    @Override
    public boolean isAutoExpand() {
      return true;
    }
  }
}
