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
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
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
public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNode, XCompositeNode, XValueNodePresentationConfigurator.ConfigurableXValueNode, RestorableStateNode {
  public static final Comparator<XValueNodeImpl> COMPARATOR = new Comparator<XValueNodeImpl>() {
    @Override
    public int compare(XValueNodeImpl o1, XValueNodeImpl o2) {
      //noinspection ConstantConditions
      return StringUtil.naturalCompare(o1.getName(), o2.getName());
    }
  };

  private final String myName;
  @Nullable
  private String myRawValue;
  private XFullValueEvaluator myFullValueEvaluator;
  private boolean myChanged;
  private XValuePresentation myValuePresentation;

  //todo[nik] annotate 'name' with @NotNull
  public XValueNodeImpl(XDebuggerTree tree, @Nullable XDebuggerTreeNode parent, String name, @NotNull XValue value) {
    super(tree, parent, value);
    myName = name;

    value.computePresentation(this, XValuePlace.TREE);

    // add "Collecting" message only if computation is not yet done
    if (!isComputed()) {
      if (myName != null) {
        myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
        myText.append(XDebuggerUIConstants.EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    }
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, type, value, hasChildren, this);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator,
                              @NonNls @Nullable String value, boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, type, separator, value, hasChildren, this);
  }

  @Override
  public void setPresentation(@Nullable Icon icon,
                              @NonNls @Nullable String type,
                              @NonNls @NotNull String value,
                              @Nullable NotNullFunction<String, String> valuePresenter,
                              boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, type, value, valuePresenter, hasChildren, this);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, presentation, hasChildren, this);
  }

  @Override
  public void setPresentation(@Nullable Icon icon,
                              @NonNls @Nullable String type,
                              @NonNls @NotNull String separator,
                              @NonNls @NotNull String value,
                              final @Nullable NotNullFunction<String, String> valuePresenter,
                              boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, type, separator, valuePresenter, hasChildren, this);
  }

  @Override
  public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresentation, boolean hasChildren) {
    setIcon(icon);
    myValuePresentation = valuePresentation;
    myRawValue = XValuePresentationUtil.computeValueText(valuePresentation);

    updateText();
    setLeaf(!hasChildren);
    fireNodeChanged();
    myTree.nodeLoaded(this, myName);
  }

  @Override
  public void setFullValueEvaluator(@NotNull final XFullValueEvaluator fullValueEvaluator) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        myFullValueEvaluator = fullValueEvaluator;
        fireNodeChanged();
      }
    });
  }

  private void updateText() {
    myText.clear();
    XValueMarkers<?, ?> markers = myTree.getValueMarkers();
    if (markers != null) {
      ValueMarkup markup = markers.getMarkup(myValueContainer);
      if (markup != null) {
        myText.append("[" + markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }
    }
    appendName();
    buildText(myValuePresentation, myText);
  }

  private void appendName() {
    if (!StringUtil.isEmpty(myName)) {
      SimpleTextAttributes attributes = myChanged ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES;
      XValuePresentationUtil.renderValue(myName, myText, attributes, MAX_VALUE_LENGTH, null);
    }
  }

  public static void buildText(@NotNull XValuePresentation valuePresenter, @NotNull final ColoredTextContainer text) {
    XValuePresentationUtil.appendSeparator(text, valuePresenter.getSeparator());
    String type = valuePresenter.getType();
    if (type != null) {
      text.append("{" + type + "} ", XDebuggerUIConstants.TYPE_ATTRIBUTES);
    }
    valuePresenter.renderValue(new XValueTextRendererImpl(text));
  }

  @Override
  public void markChanged() {
    if (myChanged) return;

    ApplicationManager.getApplication().assertIsDispatchThread();
    myChanged = true;
    if (myName != null && myValuePresentation != null) {
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
  public XValuePresentation getValuePresentation() {
    return myValuePresentation;
  }

  @Nullable
  public String getRawValue() {
    return myRawValue;
  }

  public boolean isComputed() {
    return myValuePresentation != null;
  }

  public void setValueModificationStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myRawValue = null;
    myText.clear();
    appendName();
    XValuePresentationUtil.appendSeparator(myText, myValuePresentation.getSeparator());
    myText.append(XDebuggerUIConstants.MODIFYING_VALUE_MESSAGE, XDebuggerUIConstants.MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES);
    setLeaf(true);
    fireNodeStructureChanged();
  }

  @Override
  public String toString() {
    return getName();
  }
}