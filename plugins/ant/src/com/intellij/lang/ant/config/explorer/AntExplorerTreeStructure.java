/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class AntExplorerTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.explorer.AntExplorerTreeStructure");
  private final Project myProject;
  private final Object myRoot = new Object();
  private boolean myFilteredTargets = false;
  private static final Comparator<AntBuildTarget> ourTargetComparator = (target1, target2) -> {
    final String name1 = target1.getDisplayName();
    if (name1 == null) return Integer.MIN_VALUE;
    final String name2 = target2.getDisplayName();
    if (name2 == null) return Integer.MAX_VALUE;
    return name1.compareToIgnoreCase(name2);
  };

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
    
    if (element instanceof AntBuildFileBase) {
      return new AntBuildFileNodeDescriptor(myProject, parentDescriptor, (AntBuildFileBase)element);
    }
    
    if (element instanceof AntBuildTargetBase) {
      return new AntTargetNodeDescriptor(myProject, parentDescriptor, (AntBuildTargetBase)element);
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
      return configuration.getBuildFileList().isEmpty() ? new Object[]{AntBundle.message("ant.tree.structure.no.build.files.message")} : configuration.getBuildFiles();
    }

    if (element instanceof AntBuildFile) {
      final AntBuildFile buildFile = (AntBuildFile)element;
      final AntBuildModel model = buildFile.getModel();

      final List<AntBuildTarget> targets =
        new ArrayList<>(Arrays.asList(myFilteredTargets ? model.getFilteredTargets() : model.getTargets()));
      Collections.sort(targets, ourTargetComparator);

      final List<AntBuildTarget> metaTargets = Arrays.asList(configuration.getMetaTargets(buildFile));
      Collections.sort(metaTargets, ourTargetComparator);
      targets.addAll(metaTargets);

      return targets.toArray(new AntBuildTarget[targets.size()]);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
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
