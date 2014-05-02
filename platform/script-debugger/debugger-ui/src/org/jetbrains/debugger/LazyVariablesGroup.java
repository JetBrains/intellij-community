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

  private final int start;
  private final int end;
  private final VariableContext context;

  private final ValueType componentType;
  private final boolean sparse;

  public LazyVariablesGroup(@NotNull ObjectValue value, int start, int end, @NotNull VariableContext context) {
    this(value, start, end, context, null, true);
  }

  public LazyVariablesGroup(@NotNull ObjectValue value, int start, int end, @NotNull VariableContext context, @Nullable ValueType componentType, boolean sparse) {
    super(String.format("[%,d \u2026 %,d]", start, end));

    this.value = value;

    this.start = start;
    this.end = end;

    this.context = context;

    this.componentType = componentType;
    this.sparse = sparse;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    int bucketThreshold = XCompositeNode.MAX_CHILDREN_TO_SHOW;
    int size = end - start;
    if (!sparse && size > bucketThreshold) {
      node.addChildren(XValueChildrenList.topGroups(computeNotSparseGroups(value, context, start, end, bucketThreshold)), true);
      return;
    }

    value.getIndexedProperties(start, end + 1, bucketThreshold, new VariableView.ObsolescentIndexedVariablesConsumer(node) {
      @Override
      public void consumeRanges(@Nullable int[] ranges) {
        if (ranges == null) {
          XValueChildrenList groupList = new XValueChildrenList();
          addGroups(value, GROUP_FACTORY, groupList, start, end, XCompositeNode.MAX_CHILDREN_TO_SHOW, context);
          node.addChildren(groupList, true);
        }
        else {
          addRanges(value, ranges, node, context, true);
        }
      }

      @Override
      public void consumeVariables(@NotNull List<Variable> variables) {
        node.addChildren(Variables.createVariablesList(variables, context), true);
      }
    }, componentType);
  }

  @NotNull
  public static List<XValueGroup> computeNotSparseGroups(@NotNull ObjectValue value, @NotNull VariableContext context, int from, int to, int bucketThreshold) {
    int size = to - from;
    int bucketSize = (int)Math.pow(bucketThreshold, Math.ceil(Math.log(size) / Math.log(bucketThreshold)) - 1);
    List<XValueGroup> groupList = new ArrayList<XValueGroup>((int)Math.ceil(size / bucketSize));
    for (; from < to; from += bucketSize) {
      groupList.add(new LazyVariablesGroup(value, from, from + (Math.min(bucketSize, to - from) - 1), context, ValueType.NUMBER, false));
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