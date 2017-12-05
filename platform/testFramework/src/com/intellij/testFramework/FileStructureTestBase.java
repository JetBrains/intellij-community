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
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class FileStructureTestBase extends CodeInsightFixtureTestCase {

  protected FileStructureTestFixture myPopupFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPopupFixture = new FileStructureTestFixture(myFixture);
  }

  protected void configureDefault() {
    myFixture.configureByFile(PathUtil.makeFileName(getTestName(false), getFileExtension()));
  }

  protected abstract String getFileExtension();

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myPopupFixture);
      myPopupFixture = null;
    }
    finally {
      super.tearDown();
    }
  }

  protected void checkTree(String filter) {
    checkTree(filter, true);
  }

  protected void checkTree() {
    checkTree(null, true);
  }

  protected void checkTree(boolean expandAll) {
    checkTree(null, expandAll);
  }
  
  protected void checkTree(@Nullable String filter, boolean expandAll) {
    configureDefault();
    myPopupFixture.update();
    if (filter != null) {
      setSearchFilter(filter);
    }
    if (expandAll) {
      TreeUtil.expandAll(myPopupFixture.getTree());
    }
    checkResult();
  }

  protected void setSearchFilter(@NotNull String filter) {
    myPopupFixture.getPopup().setSearchFilterForTests(filter);
    PlatformTestUtil.waitForPromise(myPopupFixture.getPopup().rebuildAndUpdate());
    myPopupFixture.getSpeedSearch().findAndSelectElement(filter);
  }

  protected void checkResult() {
    String expectedFileName = getTestDataPath() + "/" + PathUtil.makeFileName(getTestName(false), "tree");
    assertSameLinesWithFile(expectedFileName, PlatformTestUtil.print(myPopupFixture.getTree(), true).trim());
  }
}
