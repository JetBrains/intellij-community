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

import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;

import javax.swing.*;

/**
 * @author yole
 */
public class TreeUIHelperImpl extends TreeUIHelper {
  public void installToolTipHandler(final JTree tree) {
    if (tree instanceof Tree) return;
    TreeExpandTipHandler.install(tree);
  }

  public void installToolTipHandler(final JTable table) {
    if (table instanceof JBTable) return;
    TableExpandTipHandler.install(table);
  }

  @Override
  public void installToolTipHandler(JList list) {
    if (list instanceof JBList) return;
    ListExpandTipHandler.install(list);
  }

  public void installEditSourceOnDoubleClick(final JTree tree) {
    EditSourceOnDoubleClickHandler.install(tree);
  }

  public void installTreeSpeedSearch(final JTree tree) {
    new TreeSpeedSearch(tree);
  }

  public void installListSpeedSearch(final JList list) {
    new ListSpeedSearch(list);
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