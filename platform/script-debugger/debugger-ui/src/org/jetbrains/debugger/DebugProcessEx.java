package org.jetbrains.debugger;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.frame.StackFrameImpl;

import javax.swing.*;
import java.util.List;

// todo should not extends MemberFilter
public interface DebugProcessEx extends MemberFilter {
  @Nullable
  SourceInfo getSourceInfo(@Nullable Script script, @NotNull CallFrame frame);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull String scriptUrl, int line, int column);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull Script script, int line, int column);

  Vm getVm();

  @NotNull
  String propertyNamesToString(@NotNull List<String> list, boolean quotedAware);

  void computeObjectPresentation(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon);

  @NotNull
  XDebuggerEvaluator createFrameEvaluator(@NotNull StackFrameImpl frame);

  final class SimpleDebugProcessEx implements DebugProcessEx {
    public static final DebugProcessEx INSTANCE = new SimpleDebugProcessEx();

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
      // todo
      StringBuilder builder = new StringBuilder();
      for (int i = list.size() - 1; i >= 0; i--) {
        String name = list.get(i);
        boolean quoted = quotedAware && (name.charAt(0) == '"' || name.charAt(0) == '\'');
        boolean useKeyNotation = !quoted;
        if (builder.length() != 0) {
          builder.append(useKeyNotation ? '.' : '[');
        }
        if (useKeyNotation) {
          builder.append(name);
        }
        else {
          builder.append(name);
          builder.append(']');
        }
      }
      return builder.toString();
    }

    @Override
    public void computeObjectPresentation(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XValueNode node, @NotNull Icon icon) {
      VariableView.setObjectPresentation(value, icon, node);
    }

    @NotNull
    @Override
    public XDebuggerEvaluator createFrameEvaluator(@NotNull StackFrameImpl frame) {
      throw new UnsupportedOperationException();
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