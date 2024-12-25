// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class BaseTestProxyNodeDescriptor<T extends AbstractTestProxy> extends NodeDescriptor<T> {
  private final @NotNull T myTestProxy;

  public BaseTestProxyNodeDescriptor(final @Nullable Project project,
                                     final @NotNull T testProxy,
                                     final @Nullable NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
    myTestProxy = testProxy;
    myName = testProxy.getName();
  }

  @Override
  public int getWeight() {
    return myTestProxy.isLeaf() ? 10 : 5;
  }

  public String getName() {
    return myTestProxy.getName();
  }

  @Override
  public boolean expandOnDoubleClick() {
    return !getElement().isLeaf();
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public T getElement() {
    return myTestProxy;
  }
}
