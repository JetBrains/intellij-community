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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.xdebugger.frame.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.debugger.values.ObjectValue;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TestCompositeNode implements XCompositeNode {
  private final AsyncPromise<XValueChildrenList> result = new AsyncPromise<>();
  private final XValueChildrenList children = new XValueChildrenList();

  private final XValueGroup valueGroup;
  public Content content;

  public TestCompositeNode() {
    valueGroup = null;
  }

  public TestCompositeNode(@NotNull XValueGroup group) {
    valueGroup = group;
  }

  @NotNull
  public XValueGroup getValueGroup() {
    return valueGroup;
  }

  @Override
  public void addChildren(@NotNull XValueChildrenList children, boolean last) {
    for (XValueGroup group : children.getTopGroups()) {
      this.children.addTopGroup(group);
    }
    for (int i = 0; i < children.size(); i++) {
      this.children.add(children.getName(i), children.getValue(i));
    }
    for (XValueGroup group : children.getBottomGroups()) {
      this.children.addBottomGroup(group);
    }

    if (last) {
      result.setResult(this.children);
    }
  }

  @Override
  public void tooManyChildren(int remaining) {
    result.setResult(children);
  }

  @Override
  public void setAlreadySorted(boolean alreadySorted) {
  }

  @Override
  public void setErrorMessage(@NotNull String errorMessage) {
    result.setError(errorMessage);
  }

  @Override
  public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
    setErrorMessage(errorMessage);
  }

  @Override
  public void setMessage(@NotNull String message, @Nullable Icon icon, @NotNull SimpleTextAttributes attributes, @Nullable XDebuggerTreeNodeHyperlink link) {
  }

  @Override
  public boolean isObsolete() {
    return false;
  }

  @NotNull
  public Promise<XValueChildrenList> getResult() {
    return result;
  }

  @NotNull
  public Promise<Content> loadContent(@NotNull final Condition<XValueGroup> groupContentResolveCondition, @NotNull final Condition<VariableView> valueSubContentResolveCondition) {
    assert content == null;

    content = new Content();
    return result.thenAsync(new Function<XValueChildrenList, Promise<Content>>() {
      private void resolveGroups(@NotNull List<XValueGroup> valueGroups, @NotNull List<TestCompositeNode> resultNodes, @NotNull List<Promise<?>> promises) {
        for (XValueGroup group : valueGroups) {
          TestCompositeNode node = new TestCompositeNode(group);
          boolean computeChildren = groupContentResolveCondition.value(group);
          if (computeChildren) {
            group.computeChildren(node);
          }
          resultNodes.add(node);
          if (computeChildren) {
            promises.add(node.loadContent(Conditions.alwaysFalse(), valueSubContentResolveCondition));
          }
        }
      }

      @NotNull
      @Override
      public Promise<Content> fun(XValueChildrenList list) {
        List<Promise<?>> promises = new ArrayList<>();
        resolveGroups(children.getTopGroups(), content.topGroups, promises);

        for (int i = 0; i < children.size(); i++) {
          XValue value = children.getValue(i);
          TestValueNode node = new TestValueNode();
          node.myName = children.getName(i);
          value.computePresentation(node, XValuePlace.TREE);
          content.values.add(node);
          promises.add(node.getResult());

          // myHasChildren could be not computed yet
          if (value instanceof VariableView && ((VariableView)value).getValue() instanceof ObjectValue && valueSubContentResolveCondition.value((VariableView)value)) {
            promises.add(node.loadChildren(value));
          }
        }

        resolveGroups(children.getBottomGroups(), content.bottomGroups, promises);

        return Promises.all(promises, content);
      }
    });
  }
}