// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.actions.ExternalReportImportManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.impl.TestOnlyThreading;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;

public class ExternalReportImportTest extends BasePlatformTestCase {

  public void testOpenSuiteFromFileOnEdtWithoutWriteIntentLock() throws Exception {
    File suiteFile = FileUtil.createTempFile("coverage", ".ic", true);
    FileUtil.copy(new File(PluginPathManager.getPluginHomePath("coverage"), "testData/simple/simple$foo_in_simple.ic"), suiteFile);
    VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(suiteFile.getAbsolutePath());
    assertNotNull(vFile);

    Ref<Boolean> opened = Ref.create(false);
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
      assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired());
      opened.set(ExternalReportImportManager.getInstance(getProject())
                   .openSuiteFromFile(vFile, ExternalReportImportManager.Source.ACTION));
    });
    assertTrue(opened.get());
  }
}
