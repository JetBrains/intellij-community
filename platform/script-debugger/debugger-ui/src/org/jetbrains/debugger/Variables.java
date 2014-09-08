package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;
import org.jetbrains.debugger.values.ValueType;

import java.util.*;
import java.util.regex.Pattern;

public final class Variables {
  private static final Pattern UNNAMED_FUNCTION_PATTERN = Pattern.compile("^function[\\t ]*\\(");

  private static final Comparator<Variable> NATURAL_NAME_COMPARATOR = new Comparator<Variable>() {
    @Override
    public int compare(@NotNull Variable o1, @NotNull Variable o2) {
      return naturalCompare(o1.getName(), o2.getName());
    }
  };

  public static void processScopeVariables(@NotNull Scope scope,
                                           @NotNull XCompositeNode node,
                                           @NotNull final VariableContext context,
                                           @Nullable final ActionCallback compoundActionCallback) {
    final boolean isLast = compoundActionCallback == null;
    AsyncResult<?> result = ObsolescentAsyncResults.consume(scope.getVariables(), node, new PairConsumer<List<Variable>, XCompositeNode>() {
      @Override
      public void consume(List<Variable> variables, XCompositeNode node) {
        final MemberFilter memberFilter = context.createMemberFilter();
        Collection<Variable> additionalVariables = memberFilter.getAdditionalVariables();
        List<Variable> properties = new ArrayList<Variable>(variables.size() + additionalVariables.size());
        List<Variable> functions = new SmartList<Variable>();
        for (Variable variable : variables) {
          if (memberFilter.isMemberVisible(variable, false)) {
            Value value = variable.getValue();
            if (value != null &&
                value.getType() == ValueType.FUNCTION &&
                value.getValueString() != null &&
                !UNNAMED_FUNCTION_PATTERN.matcher(value.getValueString()).lookingAt()) {
              functions.add(variable);
            }
            else {
              properties.add(variable);
            }
          }
        }

        ContainerUtil.sort(properties, memberFilter.hasNameMappings() ? new Comparator<Variable>() {
          @Override
          public int compare(@NotNull Variable o1, @NotNull Variable o2) {
            return naturalCompare(memberFilter.getName(o1), memberFilter.getName(o2));
          }
        } :  NATURAL_NAME_COMPARATOR);
        sort(functions);

        for (Variable variable : additionalVariables) {
          properties.add(variable);
        }

        if (!properties.isEmpty()) {
          node.addChildren(createVariablesList(properties, context, memberFilter), functions.isEmpty() && isLast);
        }

        if (!functions.isEmpty()) {
          node.addChildren(XValueChildrenList.bottomGroup(new VariablesGroup("Functions", functions, context)), isLast);
        }
        else if (isLast && properties.isEmpty()) {
          node.addChildren(XValueChildrenList.EMPTY, true);
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

  @NotNull
  public static List<Variable> filterAndSort(@NotNull List<? extends Variable> variables, @NotNull VariableContext context, boolean filterFunctions) {
    if (variables.isEmpty()) {
      return Collections.emptyList();
    }

    MemberFilter memberFilter = context.createMemberFilter();
    Collection<Variable> additionalVariables = memberFilter.getAdditionalVariables();
    List<Variable> result = new ArrayList<Variable>(variables.size() + additionalVariables.size());
    for (Variable variable : variables) {
      if (memberFilter.isMemberVisible(variable, filterFunctions)) {
        result.add(variable);
      }
    }
    sort(result);

    for (Variable variable : additionalVariables) {
      result.add(variable);
    }
    return result;
  }

  private static void sort(@NotNull List<Variable> result) {
    ContainerUtil.sort(result, NATURAL_NAME_COMPARATOR);
  }

  // prefixed '_' must be last, fixed case sensitive natural compare
  private static int naturalCompare(@Nullable String string1, @Nullable String string2) {
    //noinspection StringEquality
    if (string1 == string2) {
      return 0;
    }
    if (string1 == null) {
      return -1;
    }
    if (string2 == null) {
      return 1;
    }

    final int string1Length = string1.length();
    final int string2Length = string2.length();
    int i = 0, j = 0;
    for (; i < string1Length && j < string2Length; i++, j++) {
      char ch1 = string1.charAt(i);
      char ch2 = string2.charAt(j);
      if ((StringUtil.isDecimalDigit(ch1) || ch1 == ' ') && (StringUtil.isDecimalDigit(ch2) || ch2 == ' ')) {
        int startNum1 = i;
        while (ch1 == ' ' || ch1 == '0') { // skip leading spaces and zeros
          startNum1++;
          if (startNum1 >= string1Length) {
            break;
          }
          ch1 = string1.charAt(startNum1);
        }
        int startNum2 = j;
        while (ch2 == ' ' || ch2 == '0') { // skip leading spaces and zeros
          startNum2++;
          if (startNum2 >= string2Length) {
            break;
          }
          ch2 = string2.charAt(startNum2);
        }
        i = startNum1;
        j = startNum2;
        // find end index of number
        while (i < string1Length && StringUtil.isDecimalDigit(string1.charAt(i))) {
          i++;
        }
        while (j < string2Length && StringUtil.isDecimalDigit(string2.charAt(j))) {
          j++;
        }
        int lengthDiff = (i - startNum1) - (j - startNum2);
        if (lengthDiff != 0) {
          // numbers with more digits are always greater than shorter numbers
          return lengthDiff;
        }
        for (; startNum1 < i; startNum1++, startNum2++) {
          // compare numbers with equal digit count
          int diff = string1.charAt(startNum1) - string2.charAt(startNum2);
          if (diff != 0) {
            return diff;
          }
        }
        i--;
        j--;
      }
      else if (ch1 != ch2) {
        if (ch1 == '_') {
          return 1;
        }
        else if (ch2 == '_') {
          return -1;
        }
        else {
          return ch1 - ch2;
        }
      }
    }
    // After the loop the end of one of the strings might not have been reached, if the other
    // string ends with a number and the strings are equal until the end of that number. When
    // there are more characters in the string, then it is greater.
    if (i < string1Length) {
      return 1;
    }
    else if (j < string2Length) {
      return -1;
    }
    return string1Length - string2Length;
  }

  @NotNull
  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, @NotNull VariableContext variableContext) {
    return createVariablesList(variables, variableContext, null);
  }

  @NotNull
  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, @NotNull VariableContext variableContext, @Nullable MemberFilter memberFilter) {
    return createVariablesList(variables, 0, variables.size(), variableContext, memberFilter);
  }

  @NotNull
  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, int from, int to, @NotNull VariableContext variableContext) {
    return createVariablesList(variables, from, to, variableContext, null);
  }

  @NotNull
  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, int from, int to, @NotNull VariableContext variableContext, @Nullable MemberFilter memberFilter) {
    XValueChildrenList list = new XValueChildrenList(to - from);
    VariableContext getterOrSetterContext = null;
    for (int i = from; i < to; i++) {
      Variable variable = variables.get(i);
      list.add(memberFilter == null ? new VariableView(variable, variableContext) : new VariableView(memberFilter.getName(variable), variable, variableContext));
      if (variable instanceof ObjectProperty) {
        ObjectProperty property = (ObjectProperty)variable;
        if (property.getGetter() != null) {
          if (getterOrSetterContext == null) {
            getterOrSetterContext = new NonWatchableVariableContext(variableContext);
          }
          list.add(new VariableView(new VariableImpl("get " + property.getName(), property.getGetter()), getterOrSetterContext));
        }
        if (property.getSetter() != null) {
          if (getterOrSetterContext == null) {
            getterOrSetterContext = new NonWatchableVariableContext(variableContext);
          }
          list.add(new VariableView(new VariableImpl("set " + property.getName(), property.getSetter()), getterOrSetterContext));
        }
      }
    }
    return list;
  }

  private static class NonWatchableVariableContext extends VariableContextWrapper {
    public NonWatchableVariableContext(VariableContext variableContext) {
      super(variableContext, null);
    }

    @Override
    public boolean watchableAsEvaluationExpression() {
      return false;
    }
  }
}