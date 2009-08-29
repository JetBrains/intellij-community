package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNode, XCompositeNode {
  private String myName;
  private String myType;
  private String myValue;
  private String mySeparator;
  private boolean myChanged;

  public XValueNodeImpl(XDebuggerTree tree, final XDebuggerTreeNode parent, final XValue value) {
    super(tree, parent, value);
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    value.computePresentation(this);
  }

  public void setPresentation(@NotNull final String name, @Nullable final Icon icon, @Nullable final String type, @NotNull final String value,
                              final boolean hasChildren) {
    setPresentation(name, icon, type, XDebuggerUIConstants.EQ_TEXT, value, hasChildren);
  }

  public void setPresentation(@NonNls @NotNull final String name, @Nullable final Icon icon, @NonNls @Nullable final String type, @NonNls @NotNull final String separator,
                              @NonNls @NotNull final String value,
                              final boolean hasChildren) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        setIcon(icon);
        myName = name;
        myValue = value;
        mySeparator = separator;
        myType = type;

        updateText();
        setLeaf(!hasChildren);
        fireNodeChanged();
        myTree.nodeLoaded(XValueNodeImpl.this, name, value);
      }
    });
  }

  private void updateText() {
    myText.clear();
    myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    myText.append(mySeparator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    if (myType != null) {
      myText.append("{" + myType + "} ", XDebuggerUIConstants.TYPE_ATTRIBUTES);
    }
    myText.append(myValue, myChanged ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void markChanged() {
    if (myChanged) return;

    ApplicationManager.getApplication().assertIsDispatchThread();
    myChanged = true;
    if (myName != null && myValue != null) {
      updateText();
      fireNodeChanged();
    }
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  public void setValueModificationStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myValue = null;
    myText.clear();
    myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    myText.append(mySeparator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myText.append(XDebuggerUIConstants.MODIFYING_VALUE_MESSAGE, XDebuggerUIConstants.MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES);
    setLeaf(true);
    fireNodeChildrenChanged();
  }
}
