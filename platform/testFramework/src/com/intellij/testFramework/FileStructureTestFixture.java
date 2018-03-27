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
    return path == null ? null : (FilteringTreeStructure.FilteringNode)
      TreeUtil.getUserObject(path.getLastPathComponent());
  }

  public Tree getTree() {
    return getPopup().getTree();
  }

  public TreeSpeedSearch getSpeedSearch() {
    return getPopup().getSpeedSearch();
  }

  public FilteringTreeStructure.FilteringNode getRootNode() {
    return (FilteringTreeStructure.FilteringNode)TreeUtil.getUserObject(getTree().getModel().getRoot());
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