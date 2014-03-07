package org.jetbrains.debugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.SourceInfo;

// todo remove when Firefox implementation will use SDK
public abstract class StackFrameImplBase extends XStackFrame {
  protected final SourceInfo sourceInfo;
  protected XDebuggerEvaluator evaluator;

  public StackFrameImplBase(@Nullable SourceInfo sourceInfo) {
    this.sourceInfo = sourceInfo;
  }

  @Override
  public final XDebuggerEvaluator getEvaluator() {
    if (evaluator == null) {
      evaluator = createEvaluator();
    }
    return evaluator;
  }

  protected abstract XDebuggerEvaluator createEvaluator();

  @Override
  public XSourcePosition getSourcePosition() {
    return sourceInfo;
  }

  protected boolean isInFileScope() {
    return false;
  }

  @Override
  public final void customizePresentation(@NotNull ColoredTextContainer component) {
    if (sourceInfo == null) {
      customizeInvalidFramePresentation(component);
      return;
    }

    String fileName = sourceInfo.getFile().getName();
    int line = sourceInfo.getLine() + 1;

    String functionName = sourceInfo.getFunctionName();
    if (functionName == null || (functionName.isEmpty() && isInFileScope())) {
      component.append(fileName + ":" + line, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      if (functionName.isEmpty()) {
        component.append("anonymous", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      }
      else {
        component.append(functionName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      component.append("(), " + fileName + ":" + line, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    component.setIcon(AllIcons.Debugger.StackFrame);
  }

  protected void customizeInvalidFramePresentation(ColoredTextContainer component) {
    super.customizePresentation(component);
  }
}