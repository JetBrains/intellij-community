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
package org.jetbrains.debugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.*;

public final class CallFrameView extends XStackFrame implements VariableContext {
  private final SourceInfo sourceInfo;
  private final DebuggerViewSupport viewSupport;
  private final CallFrame callFrame;

  private final Script script;

  private final boolean inLibraryContent;
  private XDebuggerEvaluator evaluator;

  public CallFrameView(@NotNull CallFrame callFrame, @NotNull DebuggerViewSupport viewSupport, @Nullable Script script) {
    this(callFrame, viewSupport.getSourceInfo(script, callFrame), viewSupport, script);
  }

  public CallFrameView(@NotNull CallFrame callFrame,
                       @Nullable SourceInfo sourceInfo,
                       @NotNull DebuggerViewSupport viewSupport,
                       @Nullable Script script) {
    this.sourceInfo = sourceInfo;

    this.viewSupport = viewSupport;
    this.callFrame = callFrame;
    this.script = script;

    // isInLibraryContent call could be costly, so we compute it only once (our customizePresentation called on each repaint)
    inLibraryContent = sourceInfo != null && viewSupport.isInLibraryContent(sourceInfo, script);
  }

  @Nullable
  public Script getScript() {
    return script;
  }

  @Override
  public Object getEqualityObject() {
    return callFrame.getEqualityObject();
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);
    ScopeVariablesGroup.createAndAddScopeList(node, callFrame.getVariableScopes(), this, callFrame);
  }

  @NotNull
  public CallFrame getCallFrame() {
    return callFrame;
  }

  @NotNull
  @Override
  public EvaluateContext getEvaluateContext() {
    return callFrame.getEvaluateContext();
  }

  @Nullable
  @Override
  public String getName() {
    return null;
  }

  @Nullable
  @Override
  public VariableContext getParent() {
    return null;
  }

  @Override
  public boolean watchableAsEvaluationExpression() {
    return true;
  }

  @NotNull
  @Override
  public DebuggerViewSupport getViewSupport() {
    return viewSupport;
  }

  @NotNull
  @Override
  public Promise<MemberFilter> getMemberFilter() {
    return viewSupport.getMemberFilter(this);
  }

  @NotNull
  public Promise<MemberFilter> getMemberFilter(@NotNull Scope scope) {
    return ScopeVariablesGroup.createVariableContext(scope, this, callFrame).getMemberFilter();
  }

  @Nullable
  @Override
  public Scope getScope() {
    return null;
  }

  @Override
  public final XDebuggerEvaluator getEvaluator() {
    if (evaluator == null) {
      evaluator = viewSupport.createFrameEvaluator(this);
    }
    return evaluator;
  }

  @Override
  @Nullable
  public SourceInfo getSourcePosition() {
    return sourceInfo;
  }

  @Override
  public final void customizePresentation(@NotNull ColoredTextContainer component) {
    if (sourceInfo == null) {
      String scriptName = script == null ? "unknown" : script.getUrl().trimParameters().toDecodedForm();
      int line = callFrame.getLine();
      component.append(line != -1 ? scriptName + ':' + line : scriptName, SimpleTextAttributes.ERROR_ATTRIBUTES);
      return;
    }

    String fileName = sourceInfo.getFile().getName();
    int line = sourceInfo.getLine() + 1;

    SimpleTextAttributes textAttributes = inLibraryContent ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;

    String functionName = sourceInfo.getFunctionName();
    if (functionName == null || (functionName.isEmpty() && callFrame.hasOnlyGlobalScope())) {
      component.append(fileName + ":" + line, textAttributes);
    }
    else {
      if (functionName.isEmpty()) {
        component.append("anonymous", inLibraryContent ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      }
      else {
        component.append(functionName, textAttributes);
      }
      component.append("(), " + fileName + ":" + line, textAttributes);
    }
    component.setIcon(AllIcons.Debugger.StackFrame);
  }
}