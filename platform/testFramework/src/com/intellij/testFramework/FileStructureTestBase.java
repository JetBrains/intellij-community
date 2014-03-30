/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.tree.TreeUtil;
import junit.framework.Assert;
import org.junit.Before;

import javax.swing.tree.TreePath;
import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public abstract class FileStructureTestBase extends CodeInsightFixtureTestCase {
  protected FileStructurePopup myPopup;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile(getFileName(getFileExtension()));
    myPopup = ViewStructureAction.createPopup(
      myFixture.getProject(),
      TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor()));
    assert myPopup != null;
    myPopup.createCenterPanel();
    getBuilder().getUi().getUpdater().setPassThroughMode(true);
    update();
  }

  protected abstract String getFileExtension();

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(myPopup);
    super.tearDown();
  }

  private String getFileName(String ext) {
    return getTestName(false) + (StringUtil.isEmpty(ext) ? "" : "." + ext);
  }

  protected String getTreeFileName() {
    return getFileName("tree");
  }

  protected void checkTree(String filter) throws Exception {
    myPopup.setSearchFilterForTests(filter);
    getBuilder().refilter(null, false, true);
    getBuilder().queueUpdate();
    TreeUtil.selectPath(getTree(), (TreePath)getSpeedSearch().findElement(filter));
    checkTree();
  }

  protected void checkTree() throws Exception {
    final String expected = FileUtil.loadFile(new File(getTestDataPath() + "/" + getTreeFileName()), true);
    Assert.assertEquals(expected.trim(), PlatformTestUtil.print(getTree(), true).trim());
  }


  public void update() throws InterruptedException {
    myPopup.getTreeBuilder().refilter().doWhenProcessed(new Runnable() {
      @Override
      public void run() {

        getStructure().rebuild();
        updateTree();
        getBuilder().updateFromRoot();
        TreeUtil.expandAll(getTree());
        final FilteringTreeStructure.FilteringNode node = myPopup.selectPsiElement(myPopup.getCurrentElement(getFile()));
        getBuilder().getUi().select(node, null);
      }
    });
  }

  protected Tree getTree() {
    return myPopup.getTree();
  }

  protected FilteringTreeBuilder getBuilder() {
    return myPopup.getTreeBuilder();
  }

  protected FileStructurePopup.MyTreeSpeedSearch getSpeedSearch() {
    return (FileStructurePopup.MyTreeSpeedSearch)myPopup.getSpeedSearch();
  }


  protected void updateTree() {
    updateRecursively(getRootNode());
  }

  protected FilteringTreeStructure getStructure() {
    final FilteringTreeStructure structure = (FilteringTreeStructure)getBuilder().getTreeStructure();
    assert structure != null;
    return structure;
  }

  protected FilteringTreeStructure.FilteringNode getRootNode() {
    return (FilteringTreeStructure.FilteringNode)getStructure().getRootElement();
  }

  protected void updateRecursively(final FilteringTreeStructure.FilteringNode node) {
    node.update();
    for (FilteringTreeStructure.FilteringNode child : node.children()) {
      updateRecursively(child);
    }
  }
}
