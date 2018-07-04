// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructureTestFixture implements Disposable {
  private final CodeInsightTestFixture myFixture;

  private FileStructurePopup myPopup;
  private PsiFile myFile;

  public FileStructureTestFixture(@NotNull CodeInsightTestFixture fixture) {
    myFixture = fixture;
  }

  @Nullable
  public FilteringTreeStructure.FilteringNode update() {
    FileStructurePopup popup = getPopup();
    PlatformTestUtil.waitForPromise(popup.rebuildAndUpdate());
    TreePath path = PlatformTestUtil.waitForPromise(popup.select(popup.getCurrentElement(myFile)));
    return path == null ? null : TreeUtil.getUserObject(FilteringTreeStructure.FilteringNode.class, path.getLastPathComponent());
  }

  public Tree getTree() {
    return getPopup().getTree();
  }

  public TreeSpeedSearch getSpeedSearch() {
    return getPopup().getSpeedSearch();
  }

  public FilteringTreeStructure.FilteringNode getRootNode() {
    return TreeUtil.getUserObject(FilteringTreeStructure.FilteringNode.class, getTree().getModel().getRoot());
  }

  @NotNull
  public FileStructurePopup getPopup() {
    if (myPopup == null || myFile != myFixture.getFile()) {
      if (myPopup != null) {
        Disposer.dispose(myPopup);
        myPopup = null;
      }
      myFile = myFixture.getFile();
      myPopup = ViewStructureAction.createPopup(myFixture.getProject(), TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor()));
      assert myPopup != null;
      Disposer.register(this, myPopup);
      myPopup.createCenterPanel();
    }
    return myPopup;
  }

  @Override
  public void dispose() {
    myPopup = null;
    myFile = null;
  }
}