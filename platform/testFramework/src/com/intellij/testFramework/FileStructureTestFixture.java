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
package com.intellij.testFramework;

import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructureTestFixture {
  private final PsiFile myFile;
  private FileStructurePopup myPopup;

  public FileStructureTestFixture(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    myFile = file;
    myPopup = ViewStructureAction.createPopup(project, TextEditorProvider.getInstance().getTextEditor(editor));
    assert myPopup != null;
    myPopup.createCenterPanel();
    getBuilder().getUi().getUpdater().setPassThroughMode(true);
  }

  public void dispose() {
    Disposer.dispose(myPopup);
  }

  @Nullable
  public FilteringTreeStructure.FilteringNode update() {
    final Ref<FilteringTreeStructure.FilteringNode> nodeRef = new Ref<FilteringTreeStructure.FilteringNode>();
    myPopup.getTreeBuilder().refilter().doWhenProcessed(new Runnable() {
      @Override
      public void run() {
        getStructure().rebuild();
        updateTree();
        getBuilder().updateFromRoot();
        TreeUtil.expandAll(getTree());
        nodeRef.set(myPopup.selectPsiElement(myPopup.getCurrentElement(myFile)));
        getBuilder().getUi().select(nodeRef.get(), null);
      }
    });
    return nodeRef.get();
  }

  public Tree getTree() {
    return myPopup.getTree();
  }

  public FilteringTreeBuilder getBuilder() {
    return myPopup.getTreeBuilder();
  }

  public FileStructurePopup.MyTreeSpeedSearch getSpeedSearch() {
    return (FileStructurePopup.MyTreeSpeedSearch)myPopup.getSpeedSearch();
  }


  public void updateTree() {
    updateRecursively(getRootNode());
  }

  public FilteringTreeStructure getStructure() {
    final FilteringTreeStructure structure = (FilteringTreeStructure)getBuilder().getTreeStructure();
    assert structure != null;
    return structure;
  }

  public FilteringTreeStructure.FilteringNode getRootNode() {
    return getStructure().getRootElement();
  }

  public void updateRecursively(final FilteringTreeStructure.FilteringNode node) {
    node.update();
    for (FilteringTreeStructure.FilteringNode child : node.children()) {
      updateRecursively(child);
    }
  }

  @NotNull
  public FileStructurePopup getPopup() {
    return myPopup;
  }
}