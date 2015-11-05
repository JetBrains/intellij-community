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

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.TreePath;

/**
 * @author Konstantin Bulenkov
 */
public abstract class FileStructureTestBase extends CodeInsightFixtureTestCase {

  protected FileStructureTestFixture myPopupFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPopupFixture = new FileStructureTestFixture(myFixture);
    Disposer.register(getProject(), myPopupFixture);
  }

  protected void configureDefault() {
    myFixture.configureByFile(getFileName(getFileExtension()));
  }

  protected abstract String getFileExtension();

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myPopupFixture = null;
  }

  private String getFileName(String ext) {
    return getTestName(false) + (StringUtil.isEmpty(ext) ? "" : "." + ext);
  }

  protected String getTreeFileName() {
    return getFileName("tree");
  }

  protected void checkTree(String filter) {
    configureDefault();
    myPopupFixture.update();
    myPopupFixture.getPopup().setSearchFilterForTests(filter);
    myPopupFixture.getBuilder().refilter(null, false, true);
    myPopupFixture.getBuilder().queueUpdate();
    TreeUtil.selectPath(myPopupFixture.getTree(), (TreePath)myPopupFixture.getSpeedSearch().findElement(filter));
    checkResult();
  }

  protected void checkTree() {
    configureDefault();
    myPopupFixture.update();
    checkResult();
  }

  protected void checkResult() {
    assertSameLinesWithFile(getTestDataPath() + "/" + getTreeFileName(), PlatformTestUtil.print(myPopupFixture.getTree(), true).trim());
  }
}
