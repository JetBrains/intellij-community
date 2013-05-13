/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.converter;

import com.intellij.conversion.ProjectConversionTestUtil;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class AppEngineFacetConverterTest extends PlatformTestCase {
  public void testConvert() throws IOException {
    String testDataPath = "plugins/google-app-engine/testData/conversion/appEngineFacet/";
    File testData = PathManagerEx.findFileUnderProjectHome(testDataPath + "before", getClass());
    File tempDir = FileUtil.createTempDirectory("app-engine-project", null);
    FileUtil.copyDir(testData, tempDir);
    ProjectConversionTestUtil.convert(tempDir.getAbsolutePath());
    File expectedDataDir = PathManagerEx.findFileUnderProjectHome(testDataPath + "after", getClass());
    PlatformTestUtil.assertDirectoriesEqual(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(expectedDataDir),
                                            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir),
                                            new VirtualFileFilter() {
                                              @Override
                                              public boolean accept(VirtualFile file) {
                                                return !file.getName().startsWith(ProjectConversionUtil.PROJECT_FILES_BACKUP);
                                              }
                                            });
  }
}
