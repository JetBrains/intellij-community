// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
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
      if (myPopupFixture != null) {
        Disposer.dispose(myPopupFixture);
        myPopupFixture = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void checkTree(String filter) {
    checkTree(filter, true);
  }

  protected void checkTree() {
    EdtTestUtilKt.runInEdtAndWait(true, () -> {
      checkTree(null, true);
      return null;
    });
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
      PlatformTestUtil.expandAll(myPopupFixture.getTree());
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
    PlatformTestUtil.waitWhileBusy(myPopupFixture.getTree());
    assertSameLinesWithFile(expectedFileName, PlatformTestUtil.print(myPopupFixture.getTree(), true).trim());
  }
}
