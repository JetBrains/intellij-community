// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class AntExplorerTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance(AntExplorerTreeStructure.class);
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

  AntExplorerTreeStructure(final Project project) {
    myProject = project;
  }

  @Override
  public boolean isToBuildChildrenInBackground(@NotNull final Object element) {
    return true;
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    return element != myRoot && !(element instanceof AntBuildFile);
  }

  @Override
  @NotNull
  public AntNodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
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
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    final AntConfiguration configuration = AntConfiguration.getInstance(myProject);
    if (element == myRoot) {
      if (!configuration.isInitialized()) {
        return new Object[] {AntBundle.message("progress.text.loading.ant.config")};
      }
      return configuration.getBuildFiles();
    }

    if (element instanceof AntBuildFile) {
      final AntBuildFile buildFile = (AntBuildFile)element;
      final AntBuildModel model = buildFile.getModel();

      final List<AntBuildTarget> targets =
        new ArrayList<>(Arrays.asList(myFilteredTargets ? model.getFilteredTargets() : model.getTargets()));
      targets.sort(ourTargetComparator);

      final List<AntBuildTarget> metaTargets = Arrays.asList(configuration.getMetaTargets(buildFile));
      metaTargets.sort(ourTargetComparator);
      targets.addAll(metaTargets);

      return targets.toArray(new AntBuildTarget[0]);
    }

    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public Object getParentElement(@NotNull Object element) {
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

  @NotNull
  @Override
  public Object getRootElement() {
    return myRoot;
  }

  public void setFilteredTargets(boolean value) {
    myFilteredTargets = value;
  }

  private final class RootNodeDescriptor extends AntNodeDescriptor {
    RootNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
      super(project, parentDescriptor);
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
    TextInfoNodeDescriptor(Project project, NodeDescriptor parentDescriptor, @Nls String text) {
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
  }
}
