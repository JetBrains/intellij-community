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
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Comparator;

/**
 * @author nik
 */
public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNode, XCompositeNode {
  public static final Comparator<XValueNodeImpl> COMPARATOR = new Comparator<XValueNodeImpl>() {
    @Override
    public int compare(XValueNodeImpl o1, XValueNodeImpl o2) {
      return StringUtil.naturalCompare(o1.getName(), o2.getName());
    }
  };

  public static final XValuePresenter DEFAULT_VALUE_PRESENTER = new StringValuePresenter(-1, null) {
    @Override
    public void append(String value, SimpleColoredText text, boolean changed) {
      doAppend(value, text, changed ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  };

  private String myName;
  private String myType;
  @Nullable
  private String myValue;
  private XFullValueEvaluator myFullValueEvaluator;
  private String mySeparator;
  private boolean myChanged;
  private XValuePresenter myValuePresenter;

  public XValueNodeImpl(XDebuggerTree tree, final XDebuggerTreeNode parent, String name, final @NotNull XValue value) {
    super(tree, parent, value);
    myName = name;
    if (myName != null) {
      myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
      myText.append(XDebuggerUIConstants.EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    value.computePresentation(this, XValuePlace.TREE);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @Nullable String value, boolean hasChildren) {
    setPresentation(icon, type, XDebuggerUIConstants.EQ_TEXT, value, hasChildren);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator,
                              @NonNls @Nullable String value, boolean hasChildren) {
    setPresentation(null, icon, type, separator, value, hasChildren);
  }

  @Override
  public void setPresentation(@Nullable Icon icon,
                              @NonNls @Nullable String type,
                              @NonNls @NotNull String value,
                              @Nullable NotNullFunction<String, String> valuePresenter,
                              boolean hasChildren) {
    setPresentation(icon, type, XDebuggerUIConstants.EQ_TEXT, value, valuePresenter, hasChildren);
  }


  public void setPresentation(final String name, @Nullable final Icon icon, @Nullable final String type, @NotNull final String value,
                              final boolean hasChildren) {
    setPresentation(name, icon, type, XDebuggerUIConstants.EQ_TEXT, value, hasChildren);
  }

  @Override
  public void setPresentation(@Nullable Icon icon,
                              @NonNls @Nullable String type,
                              @NonNls @NotNull String separator,
                              @NonNls @NotNull String value,
                              final  @Nullable NotNullFunction<String, String> valuePresenter,
                              boolean hasChildren) {
    setPresentation(null, icon, type, separator, value, valuePresenter == null ? null : new XValuePresenterAdapter(valuePresenter), hasChildren, false);
  }

  public void setPresentation(@NonNls final String name, @Nullable final Icon icon, @NonNls @Nullable final String type, @NonNls @NotNull final String separator,
                              @NonNls @Nullable final String value, final boolean hasChildren) {
    setPresentation(name, icon, type, separator, value, null, hasChildren, false);
  }

  @Override
  public void setGroupingPresentation(@Nullable Icon icon, @NonNls @Nullable String value, @Nullable XValuePresenter valuePresenter, boolean expand) {
    setPresentation(null, icon, null, value == null ? "" : XDebuggerUIConstants.EQ_TEXT, value, valuePresenter, true, expand);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String value, @Nullable XValuePresenter valuePresenter, boolean hasChildren) {
    setPresentation(null, icon, null, XDebuggerUIConstants.EQ_TEXT, value, valuePresenter, hasChildren, false);
  }

  private void setPresentation(@NonNls final String name, @Nullable final Icon icon, @NonNls @Nullable final String type, @NonNls @NotNull final String separator,
                               @NonNls @Nullable final String value, @Nullable final XValuePresenter valuePresenter, final boolean hasChildren, final boolean expand) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      public void run() {
        setIcon(icon);
        if (name != null) {
          myName = name;
        }
        myValue = value;
        mySeparator = separator;
        myType = type;
        myValuePresenter = valuePresenter != null ? valuePresenter : DEFAULT_VALUE_PRESENTER;

        updateText();
        setLeaf(!hasChildren);
        fireNodeChanged();
        myTree.nodeLoaded(XValueNodeImpl.this, myName, value);
        if (expand) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (!isObsolete()) {
                myTree.expandPath(getPath());
              }
            }
          });
        }
      }
    });
  }

  public void setFullValueEvaluator(@NotNull final XFullValueEvaluator fullValueEvaluator) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      public void run() {
        myFullValueEvaluator = fullValueEvaluator;
        fireNodeChanged();
      }
    });
  }

  private void updateText() {
    myText.clear();
    XValueMarkers<?,?> markers = ((XDebugSessionImpl)myTree.getSession()).getValueMarkers();
    if (markers != null) {
      ValueMarkup markup = markers.getMarkup(myValueContainer);
      if (markup != null) {
        myText.append("[" + markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }
    }
    myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    if (!StringUtil.isEmpty(mySeparator)) {
      myText.append(mySeparator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    if (myType != null) {
      myText.append("{" + myType + "} ", XDebuggerUIConstants.TYPE_ATTRIBUTES);
    }
    if (myValue != null) {
      myValuePresenter.append(myValue, myText, myChanged);
    }
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
  public XFullValueEvaluator getFullValueEvaluator() {
    return myFullValueEvaluator;
  }

  @Nullable
  @Override
  protected XDebuggerTreeNodeHyperlink getLink() {
    if (myFullValueEvaluator != null) {
      return new XDebuggerTreeNodeHyperlink(myFullValueEvaluator.getLinkText()) {
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
  public String getSeparator() {
    return mySeparator;
  }

  @Nullable
  public String getType() {
    return myType;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  public boolean isComputed() {
    return myName != null && myValuePresenter != null;
  }

  public void setValueModificationStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myValue = null;
    myText.clear();
    myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    myText.append(mySeparator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myText.append(XDebuggerUIConstants.MODIFYING_VALUE_MESSAGE, XDebuggerUIConstants.MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES);
    setLeaf(true);
    fireNodeStructureChanged();
  }

  @Override
  public String toString() {
    return getName();
  }
}