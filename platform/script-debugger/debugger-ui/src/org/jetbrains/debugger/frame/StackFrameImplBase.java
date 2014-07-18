package org.jetbrains.debugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
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
  @Nullable
  public SourceInfo getSourcePosition() {
    return sourceInfo;
  }

  protected boolean isInFileScope() {
    return false;
  }

  protected boolean isInLibraryContent() {
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

    boolean isInLibraryContent = isInLibraryContent();
    SimpleTextAttributes textAttributes = isInLibraryContent ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;

    String functionName = sourceInfo.getFunctionName();
    if (functionName == null || (functionName.isEmpty() && isInFileScope())) {
      component.append(fileName + ":" + line, textAttributes);
    }
    else {
      if (functionName.isEmpty()) {
        component.append("anonymous", isInLibraryContent ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      }
      else {
        component.append(functionName, textAttributes);
      }
      component.append("(), " + fileName + ":" + line, textAttributes);
    }
    component.setIcon(AllIcons.Debugger.StackFrame);
  }

  protected void customizeInvalidFramePresentation(ColoredTextContainer component) {
    super.customizePresentation(component);
  }
}