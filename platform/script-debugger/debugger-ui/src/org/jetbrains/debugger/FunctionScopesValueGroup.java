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
        Promise.logError(CommandProcessor.LOG, error);
        node.setErrorMessage(error.getMessage());
      }
    });
  }
}