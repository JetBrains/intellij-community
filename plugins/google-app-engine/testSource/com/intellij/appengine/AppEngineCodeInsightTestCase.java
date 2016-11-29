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
package com.intellij.appengine;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.CommonProcessors;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public abstract class AppEngineCodeInsightTestCase extends UsefulTestCase {
  @NonNls private static final String DEFAULT_VERSION = "1.3.7";
  private JavaModuleFixtureBuilder myModuleBuilder;
  private IdeaProjectTestFixture myProjectFixture;
  protected CodeInsightTestFixture myCodeInsightFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = JavaTestFixtureFactory.createFixtureBuilder(getName());
    myModuleBuilder = fixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    myProjectFixture = fixtureBuilder.getFixture();
    myCodeInsightFixture = createCodeInsightFixture(getBaseDirectoryPath());
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        addAppEngineSupport(myProjectFixture.getModule());
      }
    }.execute();
  }

  protected abstract String getBaseDirectoryPath();

  private void addAppEngineSupport(Module module) {
    final AppEngineFacet appEngine = FacetManager.getInstance(module).addFacet(AppEngineFacet.getFacetType(), "AppEngine", null);
    appEngine.getConfiguration().setSdkHomePath(getSdkPath());

    ModuleRootModificationUtil.addModuleLibrary(module, VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, getSdkPath()) + "/lib/user/orm/jdo.jar!/");
  }

  public static String getSdkPath() {
    return FileUtil.toSystemIndependentName(new File(getTestDataPath(), "sdk/" + DEFAULT_VERSION).getAbsolutePath());
  }

  @Override
  protected void tearDown() throws Exception {
    myCodeInsightFixture.tearDown();
    super.tearDown();
  }

  protected CodeInsightTestFixture createCodeInsightFixture(final String relativeTestDataPath) throws Exception {
    final String testDataPath = new File(getTestDataPath(), relativeTestDataPath).getAbsolutePath();
    final CodeInsightTestFixture codeInsightFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myProjectFixture);
    codeInsightFixture.setTestDataPath(testDataPath);
    final TempDirTestFixture tempDir = codeInsightFixture.getTempDirFixture();
    myModuleBuilder.addSourceContentRoot(tempDir.getTempDirPath());
    codeInsightFixture.setUp();
    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPath);
    Assert.assertNotNull("Test data directory not found: " + testDataPath, dir);
    VfsUtil.processFilesRecursively(dir, new CommonProcessors.CollectProcessor<>());
    dir.refresh(false, true);
    tempDir.copyAll(testDataPath, "", new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        return !file.getName().contains("_after");
      }
    });
    return codeInsightFixture;
  }

  private static File getTestDataPath() {
    return PathManagerEx.findFileUnderCommunityHome("plugins/google-app-engine/testData");
  }
}
