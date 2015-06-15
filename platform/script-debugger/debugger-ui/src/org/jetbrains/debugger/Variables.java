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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.ObsolescentFunction;
import org.jetbrains.concurrency.Promise;
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

  @NotNull
  public static Promise<Void> processVariables(@NotNull final VariableContext context,
                                               @NotNull final Promise<List<Variable>> variables,
                                               @NotNull final Obsolescent obsolescent,
                                               @NotNull final PairConsumer<MemberFilter, List<Variable>> consumer) {
    // start properties loading to achieve, possibly, parallel execution (properties loading & member filter computation)
    return context.getMemberFilter()
      .then(new ValueNodeAsyncFunction<MemberFilter, Void>(obsolescent) {
        @NotNull
        @Override
        public Promise<Void> fun(final MemberFilter memberFilter) {
          return variables.then(new ObsolescentFunction<List<Variable>, Void>() {
            @Override
            public boolean isObsolete() {
              return obsolescent.isObsolete();
            }

            @Override
            public Void fun(List<Variable> variables) {
              consumer.consume(memberFilter, variables);
              return null;
            }
          });
        }
      });
  }

  @NotNull
  public static Promise<Void> processScopeVariables(@NotNull final Scope scope,
                                                    @NotNull final XCompositeNode node,
                                                    @NotNull final VariableContext context,
                                                    final boolean isLast) {
    return processVariables(context, scope.getVariablesHost().get(), node, new PairConsumer<MemberFilter, List<Variable>>() {
      @Override
      public void consume(final MemberFilter memberFilter, List<Variable> variables) {
        Collection<Variable> additionalVariables = memberFilter.getAdditionalVariables();
        List<Variable> properties = new ArrayList<Variable>(variables.size() + additionalVariables.size());
        List<Variable> functions = new SmartList<Variable>();
        for (Variable variable : variables) {
          if (memberFilter.isMemberVisible(variable)) {
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

        Comparator<Variable> comparator = memberFilter.hasNameMappings() ? new Comparator<Variable>() {
          @Override
          public int compare(@NotNull Variable o1, @NotNull Variable o2) {
            return naturalCompare(memberFilter.rawNameToSource(o1), memberFilter.rawNameToSource(o2));
          }
        } : NATURAL_NAME_COMPARATOR;

        Collections.sort(properties, comparator);
        Collections.sort(functions, comparator);

        addAditionalVariables(variables, additionalVariables, properties, memberFilter);

        if (!properties.isEmpty()) {
          node.addChildren(createVariablesList(properties, context, memberFilter), functions.isEmpty() && isLast);
        }

        if (!functions.isEmpty()) {
          node.addChildren(XValueChildrenList.bottomGroup(new VariablesGroup("Functions", functions, context)), isLast);
        }
        else if (isLast && properties.isEmpty()) {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
      }
    });
  }

  @Nullable
  public static List<Variable> processNamedObjectProperties(@NotNull List<Variable> variables,
                                                            @NotNull XCompositeNode node,
                                                            @NotNull VariableContext context,
                                                            @NotNull MemberFilter memberFilter,
                                                            int maxChildrenToAdd,
                                                            boolean defaultIsLast) {
    List<Variable> list = filterAndSort(variables, memberFilter);
    if (list.isEmpty()) {
      if (defaultIsLast) {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
      return null;
    }

    int to = Math.min(maxChildrenToAdd, list.size());
    boolean isLast = to == list.size();
    node.addChildren(createVariablesList(list, 0, to, context, memberFilter), defaultIsLast && isLast);
    if (isLast) {
      return null;
    }
    else {
      node.tooManyChildren(list.size() - to);
      return list;
    }
  }

  @NotNull
  public static List<Variable> filterAndSort(@NotNull List<Variable> variables, @NotNull MemberFilter memberFilter) {
    if (variables.isEmpty()) {
      return Collections.emptyList();
    }

    Collection<Variable> additionalVariables = memberFilter.getAdditionalVariables();
    List<Variable> result = new ArrayList<Variable>(variables.size() + additionalVariables.size());
    for (Variable variable : variables) {
      if (memberFilter.isMemberVisible(variable)) {
        result.add(variable);
      }
    }
    Collections.sort(result, NATURAL_NAME_COMPARATOR);

    addAditionalVariables(variables, additionalVariables, result, memberFilter);
    return result;
  }

  private static void addAditionalVariables(@NotNull List<? extends Variable> variables,
                                            @NotNull Collection<Variable> additionalVariables,
                                            @NotNull List<Variable> result,
                                            @NotNull MemberFilter memberFilter) {
    ol: for (Variable variable : additionalVariables) {
      for (Variable frameVariable : variables) {
        if (memberFilter.rawNameToSource(frameVariable).equals(memberFilter.rawNameToSource(variable))) {
          continue ol;
        }
      }
      result.add(variable);
    }
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
  public static XValueChildrenList createVariablesList(@NotNull List<Variable> variables, int from, int to, @NotNull VariableContext variableContext, @Nullable MemberFilter memberFilter) {
    XValueChildrenList list = new XValueChildrenList(to - from);
    VariableContext getterOrSetterContext = null;
    for (int i = from; i < to; i++) {
      Variable variable = variables.get(i);
      String normalizedName = memberFilter == null ? variable.getName() : memberFilter.rawNameToSource(variable);
      list.add(new VariableView(normalizedName, variable, variableContext));
      if (variable instanceof ObjectProperty) {
        ObjectProperty property = (ObjectProperty)variable;
        if (property.getGetter() != null) {
          if (getterOrSetterContext == null) {
            getterOrSetterContext = new NonWatchableVariableContext(variableContext);
          }
          list.add(new VariableView(new VariableImpl("get " + normalizedName, property.getGetter()), getterOrSetterContext));
        }
        if (property.getSetter() != null) {
          if (getterOrSetterContext == null) {
            getterOrSetterContext = new NonWatchableVariableContext(variableContext);
          }
          list.add(new VariableView(new VariableImpl("set " + normalizedName, property.getSetter()), getterOrSetterContext));
        }
      }
    }
    return list;
  }

  private static final class NonWatchableVariableContext extends VariableContextWrapper {
    public NonWatchableVariableContext(VariableContext variableContext) {
      super(variableContext, null);
    }

    @Override
    public boolean watchableAsEvaluationExpression() {
      return false;
    }
  }
}