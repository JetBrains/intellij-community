package org.jetbrains.debugger;

import com.intellij.util.PairConsumer;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.frame.ChromeStackFrame;

import java.util.Arrays;

class FunctionScopesValueGroup extends XValueGroup {
  private final ObjectValue value;
  private final VariableContext variableContext;

  public FunctionScopesValueGroup(ObjectValue value, VariableContext context) {
    super("Function scopes");
    this.value = value;
    variableContext = context;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    //noinspection ConstantConditions
    ObsolescentAsyncResults.consume(value.asFunction(), node, new PairConsumer<FunctionValue, XCompositeNode>() {
      @Override
      public void consume(FunctionValue value, XCompositeNode node) {
        Scope[] scopes = value.getScopes();
        if (scopes == null || scopes.length == 0) {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
        else {
          ChromeStackFrame.createAndAddScopeList(node, Arrays.asList(scopes), variableContext, null);
        }
      }
    });
  }
}