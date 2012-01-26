/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  @NotNull private final T myTestProxy;

  public BaseTestProxyNodeDescriptor(@Nullable final Project project,
                                     @NotNull final T testProxy,
                                     @Nullable final NodeDescriptor parentDescriptor) {
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

  public boolean expandOnDoubleClick() {
    return !getElement().isLeaf();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public T getElement() {
    return myTestProxy;
  }

  @Override
  public String toString() {
    return myName;
  }
}
