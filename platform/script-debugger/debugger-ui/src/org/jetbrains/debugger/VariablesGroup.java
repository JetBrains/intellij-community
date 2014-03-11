package org.jetbrains.debugger;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VariablesGroup extends XValueGroup {
  private final int from;
  private final int to;
  private final List<Variable> variables;
  private final VariableContext variableContext;

  public VariablesGroup(@NotNull String name, @NotNull List<Variable> variables, VariableContext variableContext) {
    this(0, variables.size(), variables, variableContext, name);
  }

  private VariablesGroup(int from, int to, @NotNull List<Variable> variables, @NotNull VariableContext variableContext, final String name) {
    super(name);
    this.from = from;
    this.to = to;
    this.variables = variables;
    this.variableContext = variableContext;
  }

  public static VariablesGroup createArrayRangeGroup(int start, int end, List<Variable> variables, VariableContext variableContext) {
    String name = "[" + variables.get(start).getName() + " \u2026 " + variables.get(end - 1).getName() + "]";
    return new VariablesGroup(start, end, variables, variableContext, name);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);
    node.addChildren(Variables.createVariablesList(variables, from, to, variableContext), true);
  }
}