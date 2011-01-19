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
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;

/**
 * @author nik
 */
public class XValueHintTreeComponent extends AbstractValueHintTreeComponent<Pair<XValue, String>> {
  private final XValueHint myValueHint;
  private final XDebuggerTree myTree;

  public XValueHintTreeComponent(final XValueHint valueHint, final XDebuggerTree tree, final Pair<XValue, String> initialItem) {
    super(valueHint, tree, initialItem);
    myValueHint = valueHint;
    myTree = tree;
    updateTree(initialItem);
  }

  protected void updateTree(final Pair<XValue, String> selectedItem) {
    myTree.setRoot(new XValueNodeImpl(myTree, null, selectedItem.getSecond(), selectedItem.getFirst()), true);
    myValueHint.showTreePopup(this, myTree, selectedItem.getSecond());
  }

  protected void setNodeAsRoot(final Object node) {
    if (node instanceof XValueNodeImpl) {
      final XValueNodeImpl valueNode = (XValueNodeImpl)node;
      myValueHint.shiftLocation();
      Pair<XValue, String> item = Pair.create(valueNode.getValueContainer(), valueNode.getName());
      addToHistory(item);
      updateTree(item);
    }
  }
}
