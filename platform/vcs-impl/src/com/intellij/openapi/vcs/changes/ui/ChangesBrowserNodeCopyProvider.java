/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;

class ChangesBrowserNodeCopyProvider implements CopyProvider {

  @NotNull private final JTree myTree;

  ChangesBrowserNodeCopyProvider(@NotNull JTree tree) {
    myTree = tree;
  }

  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return myTree.getSelectionPath() != null;
  }

  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  public void performCopy(@NotNull DataContext dataContext) {
    Object node = ObjectUtils.assertNotNull(myTree.getSelectionPath()).getLastPathComponent();
    String text;
    if (node instanceof ChangesBrowserNode) {
      text = ((ChangesBrowserNode)node).getTextPresentation();
    }
    else {
      text = node.toString();
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }
}
