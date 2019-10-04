// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.converter;

import com.intellij.conversion.ProjectConversionTestUtil;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class AppEngineFacetConverterTest extends HeavyPlatformTestCase {
  public void testConvert() throws IOException {
    String testDataPath = "plugins/google-app-engine/testData/conversion/appEngineFacet/";
    File testData = PathManagerEx.findFileUnderProjectHome(testDataPath + "before", getClass());
    File tempDir = FileUtil.createTempDirectory("app-engine-project", null);
    FileUtil.copyDir(testData, tempDir);
    ProjectConversionTestUtil.convert(tempDir.toPath().toAbsolutePath());
    File expectedDataDir = PathManagerEx.findFileUnderProjectHome(testDataPath + "after", getClass());
    PlatformTestUtil.assertDirectoriesEqual(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(expectedDataDir),
                                            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir),
                                            file -> !file.getName().startsWith(ProjectConversionUtil.PROJECT_FILES_BACKUP));
  }
}
