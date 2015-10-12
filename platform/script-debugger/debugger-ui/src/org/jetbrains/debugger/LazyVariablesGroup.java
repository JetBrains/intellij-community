/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.ObjectValue;
import org.jetbrains.debugger.values.ValueType;

import java.util.ArrayList;
import java.util.List;

public final class LazyVariablesGroup extends XValueGroup {
  public static final ValueGroupFactory<ObjectValue> GROUP_FACTORY = new ValueGroupFactory<ObjectValue>() {
    @Override
    public XValueGroup create(@NotNull ObjectValue value, int start, int end, @NotNull VariableContext context) {
      return new LazyVariablesGroup(value, start, end, context);
    }
  };

  private final ObjectValue value;

  private final int startInclusive;
  private final int endInclusive;
  private final VariableContext context;

  private final ValueType componentType;
  private final boolean sparse;

  public LazyVariablesGroup(@NotNull ObjectValue value, int startInclusive, int endInclusive, @NotNull VariableContext context) {
    this(value, startInclusive, endInclusive, context, null, true);
  }

  public LazyVariablesGroup(@NotNull ObjectValue value, int startInclusive, int endInclusive, @NotNull VariableContext context, @Nullable ValueType componentType, boolean sparse) {
    super(String.format("[%,d \u2026 %,d]", startInclusive, endInclusive));

    this.value = value;

    this.startInclusive = startInclusive;
    this.endInclusive = endInclusive;

    this.context = context;

    this.componentType = componentType;
    this.sparse = sparse;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    int bucketThreshold = XCompositeNode.MAX_CHILDREN_TO_SHOW;
    if (!sparse && (endInclusive - startInclusive) > bucketThreshold) {
      node.addChildren(XValueChildrenList.topGroups(computeNotSparseGroups(value, context, startInclusive, endInclusive + 1, bucketThreshold)), true);
      return;
    }

    value.getIndexedProperties(startInclusive, endInclusive + 1, bucketThreshold, new VariableView.ObsolescentIndexedVariablesConsumer(node) {
      @Override
      public void consumeRanges(@Nullable int[] ranges) {
        if (ranges == null) {
          XValueChildrenList groupList = new XValueChildrenList();
          addGroups(value, GROUP_FACTORY, groupList, startInclusive, endInclusive, XCompositeNode.MAX_CHILDREN_TO_SHOW, context);
          getNode().addChildren(groupList, true);
        }
        else {
          addRanges(value, ranges, getNode(), context, true);
        }
      }

      @Override
      public void consumeVariables(@NotNull List<Variable> variables) {
        getNode().addChildren(VariablesKt.createVariablesList(variables, context, null), true);
      }
    }, componentType);
  }

  @NotNull
  public static List<XValueGroup> computeNotSparseGroups(@NotNull ObjectValue value, @NotNull VariableContext context, int fromInclusive, int toExclusive, int bucketThreshold) {
    int size = toExclusive - fromInclusive;
    int bucketSize = (int)Math.pow(bucketThreshold, Math.ceil(Math.log(size) / Math.log(bucketThreshold)) - 1);
    List<XValueGroup> groupList = new ArrayList<XValueGroup>((int)Math.ceil(size / bucketSize));
    for (; fromInclusive < toExclusive; fromInclusive += bucketSize) {
      groupList.add(new LazyVariablesGroup(value, fromInclusive, fromInclusive + (Math.min(bucketSize, toExclusive - fromInclusive) - 1), context, ValueType.NUMBER, false));
    }
    return groupList;
  }

  public static void addRanges(@NotNull ObjectValue value, int[] ranges, @NotNull XCompositeNode node, @NotNull VariableContext context, boolean isLast) {
    XValueChildrenList groupList = new XValueChildrenList(ranges.length / 2);
    for (int i = 0, n = ranges.length; i < n; i += 2) {
      groupList.addTopGroup(new LazyVariablesGroup(value, ranges[i], ranges[i + 1], context));
    }
    node.addChildren(groupList, isLast);
  }

  public static <T> void addGroups(@NotNull T data,
                                   @NotNull ValueGroupFactory<T> groupFactory,
                                   @NotNull XValueChildrenList groupList,
                                   int from,
                                   int limit,
                                   int bucketSize,
                                   @NotNull VariableContext context) {
    int to = Math.min(bucketSize, limit);
    boolean done = false;
    do {
      int groupFrom = from;
      int groupTo = to;

      from += bucketSize;
      to = from + Math.min(bucketSize, limit - from);

      // don't create group for only one member
      if (to - from == 1) {
        groupTo++;
        done = true;
      }
      groupList.addTopGroup(groupFactory.create(data, groupFrom, groupTo, context));
      if (from >= limit) {
        break;
      }
    }
    while (!done);
  }
}