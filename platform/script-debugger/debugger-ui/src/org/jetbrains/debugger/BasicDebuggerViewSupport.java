package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.frame.CallFrameView;
import org.jetbrains.debugger.values.ObjectValue;
import org.jetbrains.debugger.values.Value;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class BasicDebuggerViewSupport implements DebuggerViewSupport, MemberFilter {
  public static final DebuggerViewSupport INSTANCE = new BasicDebuggerViewSupport();

  @Nullable
  @Override
  public SourceInfo getSourceInfo(@Nullable Script script, @NotNull CallFrame frame) {
    return null;
  }

  @Nullable
  @Override
  public SourceInfo getSourceInfo(@Nullable String functionName, @NotNull String scriptUrl, int line, int column) {
    return null;
  }

  @Nullable
  @Override
  public SourceInfo getSourceInfo(@Nullable String functionName, @NotNull Script script, int line, int column) {
    return null;
  }

  @Override
  public Vm getVm() {
    return null;
  }

  @NotNull
  @Override
  public String propertyNamesToString(@NotNull List<String> list, boolean quotedAware) {
    return ValueModifierUtil.propertyNamesToString(list, quotedAware);
  }

  @Override
  public void computeObjectPresentation(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon) {
    VariableView.setObjectPresentation(value, icon, node);
  }

  @Override
  public void computeArrayPresentation(@NotNull Value value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon) {
    VariableView.setArrayPresentation(value, context, icon, node);
  }

  @NotNull
  @Override
  public XDebuggerEvaluator createFrameEvaluator(@NotNull CallFrameView frameView) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canNavigateToSource(@NotNull Variable variable, @NotNull VariableContext context) {
    return false;
  }

  @Override
  public void computeSourcePosition(@NotNull Variable variable, @NotNull VariableContext context, @NotNull XNavigatable navigatable) {
  }

  @Nullable
  @Override
  public ActionCallback computeAdditionalObjectProperties(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XCompositeNode node) {
    return null;
  }

  @NotNull
  @Override
  public MemberFilter createMemberFilter(@NotNull VariableContext context) {
    return this;
  }

  @Override
  public boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions) {
    return true;
  }

  @NotNull
  @Override
  public List<Variable> getAdditionalVariables() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String normalizeMemberName(@NotNull Variable variable) {
    return variable.getName();
  }
}