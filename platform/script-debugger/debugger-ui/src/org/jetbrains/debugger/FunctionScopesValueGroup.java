package org.jetbrains.debugger;

import com.intellij.util.PairConsumer;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.FunctionValue;

import java.util.Arrays;

class FunctionScopesValueGroup extends XValueGroup {
  private final FunctionValue value;
  private final VariableContext variableContext;

  public FunctionScopesValueGroup(@NotNull FunctionValue value, @NotNull VariableContext variableContext) {
    super("Function scopes");

    this.value = value;
    this.variableContext = variableContext;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    ObsolescentAsyncResults.consume(value.resolve(), node, new PairConsumer<FunctionValue, XCompositeNode>() {
      @Override
      public void consume(FunctionValue value, XCompositeNode node) {
        Scope[] scopes = value.getScopes();
        if (scopes == null || scopes.length == 0) {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
        else {
          ScopeVariablesGroup.createAndAddScopeList(node, Arrays.asList(scopes), variableContext, null);
        }
      }
    });
  }
}