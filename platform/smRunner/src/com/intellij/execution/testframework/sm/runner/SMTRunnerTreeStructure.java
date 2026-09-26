// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestTreeViewStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerTreeStructure extends TestTreeViewStructure<SMTestProxy>
{
  private final SMTestProxy.SMRootTestProxy myRootNode;
  private final Project myProject;

  public SMTRunnerTreeStructure(final Project project, final SMTestProxy.SMRootTestProxy rootNode) {
    myProject = project;
    myRootNode = rootNode;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public @NotNull SMTRunnerNodeDescriptor createDescriptor(final @NotNull Object element,
                                                           final NodeDescriptor parentDesc) {
    //noinspection unchecked
    return new SMTRunnerNodeDescriptor(myProject,
                                       (SMTestProxy)element,
                                       (NodeDescriptor<SMTestProxy>)parentDesc);
  }

  @Override
  public Object @NotNull [] getChildElements(final @NotNull Object element) {
    final List<? extends SMTestProxy> results =
        ((SMTestProxy)element).getChildren(getFilter());

    return results.toArray(new AbstractTestProxy[0]);
  }

  @Override
  public Object getParentElement(final @NotNull Object element) {
    return ((AbstractTestProxy)element).getParent();
  }


  @Override
  public @NotNull SMTestProxy.SMRootTestProxy getRootElement() {
    return myRootNode;
  }
}
