package org.jetbrains.debugger;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.ArrayValue;

import java.util.List;

final class LazyVariablesGroup extends XValueGroup {
  public static final ValueGroupFactory<ArrayValue> GROUP_FACTORY = new ValueGroupFactory<ArrayValue>() {
    @Override
    public XValueGroup create(@NotNull ArrayValue value, int start, int end, @NotNull VariableContext context) {
      return new LazyVariablesGroup(value, start, end, context);
    }
  };

  private final ArrayValue value;

  private final int start;
  private final int end;
  private final VariableContext context;

  public LazyVariablesGroup(@NotNull ArrayValue value, int start, int end, @NotNull VariableContext context) {
    super(String.format("[%,d \u2026 %,d]", start, end));

    this.value = value;

    this.start = start;
    this.end = end;

    this.context = context;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    value.getVariables(start, end, XCompositeNode.MAX_CHILDREN_TO_SHOW, new VariableView.ObsolescentIndexedVariablesConsumer(node) {
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
    });
  }

  public static void addRanges(@NotNull ArrayValue value, int[] ranges, @NotNull XCompositeNode node, @NotNull VariableContext context, boolean isLast) {
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