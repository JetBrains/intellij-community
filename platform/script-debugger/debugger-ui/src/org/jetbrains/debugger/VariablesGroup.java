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
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VariablesGroup extends XValueGroup {
  public static final ValueGroupFactory<List<Variable>> GROUP_FACTORY = new ValueGroupFactory<List<Variable>>() {
    @Override
    public XValueGroup create(@NotNull List<Variable> variables, int start, int end, @NotNull VariableContext context) {
      return createArrayRangeGroup(start, end, variables, context);
    }
  };

  private final int start;
  private final int end;
  private final List<Variable> variables;
  private final VariableContext context;

  public VariablesGroup(@NotNull String name, @NotNull List<Variable> variables, VariableContext context) {
    this(0, variables.size(), variables, context, name);
  }

  private VariablesGroup(int start, int end, @NotNull List<Variable> variables, @NotNull VariableContext context, @NotNull String name) {
    super(name);

    this.start = start;
    this.end = end;
    this.variables = variables;
    this.context = context;
  }

  public static VariablesGroup createArrayRangeGroup(int start, int end, List<Variable> variables, VariableContext variableContext) {
    String name = "[" + variables.get(start).getName() + " \u2026 " + variables.get(end - 1).getName() + "]";
    return new VariablesGroup(start, end, variables, variableContext, name);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);
    node.addChildren(VariablesKt.createVariablesList(variables, start, end, context, null), true);
  }
}