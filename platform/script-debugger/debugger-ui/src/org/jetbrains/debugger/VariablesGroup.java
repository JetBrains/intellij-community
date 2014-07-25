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
    node.addChildren(Variables.createVariablesList(variables, start, end, context), true);
  }
}