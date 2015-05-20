package org.jetbrains.debugger;

import com.intellij.util.ThreeState;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.frame.CallFrameView;
import org.jetbrains.debugger.values.ObjectValue;
import org.jetbrains.debugger.values.Value;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class BasicDebuggerViewSupport extends MemberFilterBase implements DebuggerViewSupport {
  protected final Promise<MemberFilter> defaultMemberFilterPromise = Promise.<MemberFilter>resolve(this);

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
  public void computeSourcePosition(@NotNull String name, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XNavigatable navigatable) {
  }

  @NotNull
  @Override
  public ThreeState computeInlineDebuggerData(@NotNull String name, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XInlineDebuggerDataCallback callback) {
    return ThreeState.UNSURE;
  }

  @Nullable
  @Override
  public Promise<Void> computeAdditionalObjectProperties(@NotNull ObjectValue value, @NotNull Variable variable, @NotNull VariableContext context, @NotNull XCompositeNode node) {
    return null;
  }

  @NotNull
  @Override
  public Promise<MemberFilter> getMemberFilter(@NotNull VariableContext context) {
    return defaultMemberFilterPromise;
  }

  @NotNull
  @Override
  public List<Variable> getAdditionalVariables() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public Value transformErrorOnGetUsedReferenceValue(@Nullable Value value, @Nullable String error) {
    return value;
  }

  @Override
  public boolean isInLibraryContent(@NotNull SourceInfo sourceInfo, @Nullable Script script) {
    return false;
  }
}