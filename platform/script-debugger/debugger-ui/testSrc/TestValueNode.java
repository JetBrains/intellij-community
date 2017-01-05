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
package org.jetbrains.debugger;

import com.intellij.openapi.util.Conditions;
import com.intellij.xdebugger.XTestValueNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;

public class TestValueNode extends XTestValueNode {
  private final AsyncPromise<XTestValueNode> result = new AsyncPromise<>();

  private volatile Content children;

  @NotNull
  public Promise<XTestValueNode> getResult() {
    return result;
  }

  @NotNull
  public Promise<Content> loadChildren(@NotNull XValue value) {
    TestCompositeNode childrenNode = new TestCompositeNode();
    value.computeChildren(childrenNode);
    return childrenNode.loadContent(Conditions.alwaysFalse(), Conditions.alwaysFalse())
      .done(content -> children = content);
  }

  @Nullable
  public Content getChildren() {
    return children;
  }

  @Override
  public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresentation, boolean hasChildren) {
    super.applyPresentation(icon, valuePresentation, hasChildren);

    result.setResult(this);
  }
}