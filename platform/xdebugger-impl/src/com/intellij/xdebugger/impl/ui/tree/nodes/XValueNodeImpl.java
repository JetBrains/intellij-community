/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author nik
 */
public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNode, XCompositeNode {
  private String myName;
  private String myType;
  private String myValue;
  private XFullValueEvaluator myFullValueEvaluator;
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

  public void setFullValueEvaluator(@NotNull final XFullValueEvaluator fullValueEvaluator) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        myFullValueEvaluator = fullValueEvaluator;
        fireNodeChanged();
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

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    String value;
    try {
      StringUtil.escapeStringCharacters(myValue.length(), myValue, null, builder);
      value = builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    myText.append(value, myChanged ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
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



  @Override
  public XDebuggerNodeLink getLink() {
    if (myFullValueEvaluator != null) {
      return new XDebuggerNodeLink(myFullValueEvaluator.getLinkText()) {
        @Override
        public void onClick(MouseEvent event) {
          DebuggerUIUtil.showValuePopup(myFullValueEvaluator, event, myTree.getProject());
        }
      };
    }
    return null;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getType() {
    return myType;
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
