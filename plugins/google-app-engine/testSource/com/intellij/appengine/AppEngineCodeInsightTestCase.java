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
      protected void run(final Result result) {
        addAppEngineSupport(myProjectFixture.getModule(), DEFAULT_VERSION);
      }
    }.execute();
  }

  protected abstract String getBaseDirectoryPath();

  private void addAppEngineSupport(Module module, String version) {
    final AppEngineFacet appEngine = FacetManager.getInstance(module).addFacet(AppEngineFacet.getFacetType(), "AppEngine", null);
    final String sdkPath = FileUtil.toSystemIndependentName(getTestDataPath()) + "sdk/" + version;
    appEngine.getConfiguration().setSdkHomePath(sdkPath);

    ModuleRootModificationUtil.addModuleLibrary(module, VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, sdkPath) + "/lib/user/orm/jdo.jar!/");
  }

  @Override
  protected void tearDown() throws Exception {
    myCodeInsightFixture.tearDown();
    super.tearDown();
  }

  protected CodeInsightTestFixture createCodeInsightFixture(final String relativeTestDataPath) throws Exception {
    final String testDataPath = getTestDataPath() + relativeTestDataPath;
    final CodeInsightTestFixture codeInsightFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myProjectFixture);
    codeInsightFixture.setTestDataPath(testDataPath);
    final TempDirTestFixture tempDir = codeInsightFixture.getTempDirFixture();
    myModuleBuilder.addSourceContentRoot(tempDir.getTempDirPath());
    codeInsightFixture.setUp();
    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPath);
    Assert.assertNotNull("Test data directory not found: " + testDataPath, dir);
    VfsUtil.processFilesRecursively(dir, new CommonProcessors.CollectProcessor<VirtualFile>());
    dir.refresh(false, true);
    tempDir.copyAll(testDataPath, "", new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        return !file.getName().contains("_after");
      }
    });
    return codeInsightFixture;
  }

  private String getTestDataPath() {
    return PathManagerEx.getHomePath(getClass()) + FileUtil.toSystemDependentName("/plugins/google-app-engine/testData/");
  }
}
