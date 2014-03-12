package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairConsumer;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.debugger.values.ValueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class Variables {
  public static final String SPECIAL_PROPERTY_PREFIX = "__";

  private static final Pattern UNNAMED_FUNCTION_PATTERN = Pattern.compile("^function[\\t ]*\\(");

  public static void consume(@NotNull Scope scope,
                             @NotNull XCompositeNode node,
                             @NotNull final VariableContext context,
                             final @Nullable ActionCallback compoundActionCallback) {
    final boolean isLast = compoundActionCallback == null;
    AsyncResult<?> result =
      ObsolescentAsyncResults.consume(scope.getVariables(), node, new PairConsumer<List<? extends Variable>, XCompositeNode>() {
        @Override
        public void consume(List<? extends Variable> variables, XCompositeNode node) {
          List<Variable> properties = new ArrayList<Variable>(variables.size());
          List<Variable> functions = new ArrayList<Variable>();
          for (Variable variable : variables) {
            if (context.getMemberFilter().isMemberVisible(variable, false)) {
              Value value = variable.getValue();
              if (value != null &&
                  value.getType() == ValueType.FUNCTION &&
                  !UNNAMED_FUNCTION_PATTERN.matcher(value.getValueString()).lookingAt()) {
                functions.add(variable);
              }
              else {
                properties.add(variable);
              }
            }
          }

          sort(properties);
          sort(functions);

          if (!properties.isEmpty()) {
            node.addChildren(createVariablesList(properties, context), functions.isEmpty() && isLast);
          }
          if (!functions.isEmpty()) {
            node.addChildren(XValueChildrenList.bottomGroup(new VariablesGroup("Functions", functions, context)), isLast);
          }
          if (!isLast) {
            compoundActionCallback.setDone();
          }
        }
      });
    if (!isLast) {
      result.notifyWhenRejected(compoundActionCallback);
    }
  }

  @Nullable
  public static List<Variable> sortFilterAndAddValueList(@NotNull List<? extends Variable> variables,
                                                         @NotNull XCompositeNode node,
                                                         @NotNull VariableContext context,
                                                         int maxChildrenToAdd,
                                                         boolean defaultIsLast) {
    List<Variable> list = filterAndSort(variables, context, true);
    if (list.isEmpty()) {
      if (defaultIsLast) {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
      return null;
    }

    int to = Math.min(maxChildrenToAdd, list.size());
    boolean isLast = to == list.size();
    node.addChildren(createVariablesList(list, 0, to, context), defaultIsLast && isLast);
    if (isLast) {
      return null;
    }
    else {
      node.tooManyChildren(list.size() - to);
      return list;
    }
  }

  public static List<Variable> filterAndSort(@NotNull List<? extends Variable> variables, @NotNull VariableContext context, boolean filterFunctions) {
    if (variables.isEmpty()) {
      return Collections.emptyList();
    }

    List<Variable> result = new ArrayList<Variable>(variables.size());
    for (Variable variable : variables) {
      if (context.getMemberFilter().isMemberVisible(variable, filterFunctions)) {
        result.add(variable);
      }
    }
    sort(result);
    return result;
  }

  private static void sort(List<Variable> result) {
    if (result.isEmpty()) {
      return;
    }

    Collections.sort(result, new Comparator<Variable>() {
      @Override
      public int compare(Variable o1, Variable o2) {
        int diff = getWidth(o1) - getWidth(o2);
        if (diff != 0) {
          return diff;
        }
        return StringUtil.naturalCompare(o1.getName(), o2.getName()) + diff;
      }

      private int getWidth(Variable var) {
        String name = var.getName();
        if (name.isEmpty()) {
          return 0;
        }

        if (name.startsWith(SPECIAL_PROPERTY_PREFIX)) {
          return 1;
        }
        else if (Character.isUpperCase(name.charAt(0))) {
          return -2;
        }
        else {
          return 0;
        }
      }
    });
  }

  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, @NotNull VariableContext variableContext) {
    return createVariablesList(variables, 0, variables.size(), variableContext);
  }

  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, int from, int to, @NotNull VariableContext variableContext) {
    XValueChildrenList list = new XValueChildrenList(to - from);
    for (int i = from; i < to; i++) {
      list.add(new VariableView(variableContext, variables.get(i)));
    }
    return list;
  }
}