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
package com.intellij.ui;

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.tree.TreePath;

/**
 * @author yole
 */
public class TreeUIHelperImpl extends TreeUIHelper {
  public void installToolTipHandler(final JTree tree) {
    if (tree instanceof Tree) return;
    new TreeExpandableItemsHandler(tree);
  }

  public void installEditSourceOnDoubleClick(final JTree tree) {
    EditSourceOnDoubleClickHandler.install(tree);
  }

  public void installTreeSpeedSearch(final JTree tree) {
    new TreeSpeedSearch(tree);
  }

  public void installTreeSpeedSearch(JTree tree, Convertor<TreePath, String> convertor, boolean canExpand) {
    new TreeSpeedSearch(tree, convertor, canExpand);
  }

  public void installListSpeedSearch(JList<?> list) {
    new ListSpeedSearch<>(list);
  }

  public <T> void installListSpeedSearch(JList<T> list, Convertor<T, String> convertor) {
    new ListSpeedSearch<>(list, (Function<T, String>)convertor::convert);
  }

  public void installEditSourceOnEnterKeyHandler(final JTree tree) {
    EditSourceOnEnterKeyHandler.install(tree);
  }

  public void installSmartExpander(final JTree tree) {
    SmartExpander.installOn(tree);
  }

  public void installSelectionSaver(final JTree tree) {
    SelectionSaver.installOn(tree);
  }
}