// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.tree.TreePath;


final class TreeUIHelperImpl extends TreeUIHelper {
  @Override
  public void installToolTipHandler(final JTree tree) {
    if (tree instanceof Tree) {
      return;
    }
    new TreeExpandableItemsHandler(tree);
  }

  @Override
  public void installEditSourceOnDoubleClick(final JTree tree) {
    EditSourceOnDoubleClickHandler.install(tree);
  }

  @Override
  public void installTreeSpeedSearch(final JTree tree) {
    TreeSpeedSearch.installOn(tree);
  }

  @Override
  public void installTreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> convertor, boolean canExpand) {
    TreeSpeedSearch.installOn(tree, canExpand, convertor.asFunction());
  }

  @Override
  public void installListSpeedSearch(JList<?> list) {
    ListSpeedSearch.installOn(list);
  }

  @Override
  public <T> void installListSpeedSearch(JList<T> list, Convertor<? super T, String> convertor) {
    ListSpeedSearch.installOn(list, convertor::convert);
  }

  @Override
  public void installEditSourceOnEnterKeyHandler(final JTree tree) {
    EditSourceOnEnterKeyHandler.install(tree);
  }

  @Override
  public void installSmartExpander(final JTree tree) {
    SmartExpander.installOn(tree);
  }

  @Override
  public void installSelectionSaver(final JTree tree) {
    SelectionSaver.installOn(tree);
  }
}