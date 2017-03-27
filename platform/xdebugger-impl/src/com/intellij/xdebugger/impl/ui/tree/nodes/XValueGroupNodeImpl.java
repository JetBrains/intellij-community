/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    super(tree, parent, group);
    setLeaf(false);
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
