package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.FunctionValue;
import org.jetbrains.rpc.CommandProcessor;

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
  public void computeChildren(@NotNull final XCompositeNode node) {
    node.setAlreadySorted(true);

    value.resolve()
      .done(new ObsolescentConsumer<FunctionValue>(node) {
      @Override
      public void consume(FunctionValue value) {
        Scope[] scopes = value.getScopes();
        if (scopes == null || scopes.length == 0) {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
        else {
          ScopeVariablesGroup.createAndAddScopeList(node, Arrays.asList(scopes), variableContext, null);
        }
      }
    })
    .rejected(new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        if (!(error instanceof Promise.MessageError)) {
          CommandProcessor.LOG.error(error);
        }
        node.setErrorMessage(error.getMessage());
      }
    });
  }
}