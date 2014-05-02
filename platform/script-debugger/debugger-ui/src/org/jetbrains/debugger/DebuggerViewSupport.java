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
import java.util.List;

// todo should not extends MemberFilter
public interface DebuggerViewSupport extends MemberFilter {
  @Nullable
  SourceInfo getSourceInfo(@Nullable Script script, @NotNull CallFrame frame);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull String scriptUrl, int line, int column);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull Script script, int line, int column);

  Vm getVm();

  @NotNull
  String propertyNamesToString(@NotNull List<String> list, boolean quotedAware);

  // Please, don't hesitate to ask to share some generic implementations. Don't reinvent the wheel and keep in mind - user expects the same UI across all IDEA-based IDEs.
  void computeObjectPresentation(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon);

  void computeArrayPresentation(@NotNull Value value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon);

  @NotNull
  XDebuggerEvaluator createFrameEvaluator(@NotNull CallFrameView frame);

  /**
   * {@link org.jetbrains.debugger.values.FunctionValue} is special case and handled by SDK
   */
  boolean canNavigateToSource(@NotNull Variable variable, @NotNull VariableContext context);

  void computeSourcePosition(@NotNull Variable variable, @NotNull VariableContext context, @NotNull XNavigatable navigatable);

  // return null if you don't need to add additional properties
  @Nullable
  ActionCallback computeAdditionalObjectProperties(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XCompositeNode node);

  class BasicDebuggerViewSupport implements DebuggerViewSupport {
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

    @Override
    public boolean isMemberVisible(@NotNull Variable variable, boolean filterFunctions) {
      return true;
    }

    @NotNull
    @Override
    public String normalizeMemberName(@NotNull Variable variable) {
      return variable.getName();
    }
  }
}