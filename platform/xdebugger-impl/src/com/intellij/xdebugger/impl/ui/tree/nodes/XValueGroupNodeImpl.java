// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XValueGroup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XValueGroupNodeImpl extends XValueContainerNode<XValueGroup> implements RestorableStateNode {
  public XValueGroupNodeImpl(XDebuggerTree tree, XDebuggerTreeNode parent, @NotNull XValueGroup group) {
    super(tree, parent, false, group);
    setIcon(group.getIcon());
    myText.append(group.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    String comment = group.getComment();
    if (comment != null) {
      XValuePresentationUtil.appendSeparator(myText, group.getSeparator());
      myText.append(comment, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    if (isExpand(group)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isObsolete()) {
          myTree.expandPath(getPath());
        }
      });
    }
    myTree.nodeLoaded(this, group.getName());
  }

  private boolean isExpand(@NotNull XValueGroup group) {
    if (group.isRestoreExpansion()) {
      final String name = group.getName();
      if (StringUtil.isNotEmpty(name) && PropertiesComponent.getInstance(getTree().getProject()).isValueSet(group.getName())) {
        return PropertiesComponent.getInstance(getTree().getProject()).getBoolean(name);
      }
    }
    return group.isAutoExpand();
  }

  public void onExpansion(boolean expanded) {
    final XValueGroup group = getValueContainer();
    if (group.isRestoreExpansion()) {
      final String name = group.getName();
      if (StringUtil.isNotEmpty(name)) {
        PropertiesComponent.getInstance(getTree().getProject()).setValue(group.getName(), String.valueOf(expanded), null);
      }
    }
  }

  @Override
  public String toString() {
    return "Group:" + myValueContainer.getName();
  }

  @Nullable
  @Override
  public String getName() {
    return myValueContainer.getName();
  }

  @Nullable
  @Override
  public String getRawValue() {
    return null;
  }

  @Override
  public boolean isComputed() {
    return true;
  }

  @Override
  public void markChanged() {
  }
}
