package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * @author nik
 */
public class XCopyValueAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    //todo[nik] is it correct? Perhaps we should use evaluator.evaluateMessage here
    String value = node.getValue();
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValue() != null;
  }
}
