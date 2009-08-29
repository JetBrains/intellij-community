package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XJumpToSourceAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XValue value = node.getValueContainer();
    value.computeSourcePosition(new XNavigatable() {
      public void setSourcePosition(@Nullable final XSourcePosition sourcePosition) {
        if (sourcePosition != null) {
          DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
            public void run() {
              Project project = node.getTree().getProject();
              if (project.isDisposed()) return;

              sourcePosition.createNavigatable(project).navigate(true);
            }
          });
        }
      }
    });
  }

}
