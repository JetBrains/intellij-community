package com.intellij.testFramework.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public abstract class CodeInsightFixtureTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  protected void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final EmptyModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(EmptyModuleFixtureBuilder.class);
    moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    tuneFixture(moduleFixtureBuilder);

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
    super.tearDown();
  }

  protected void tuneFixture(final EmptyModuleFixtureBuilder moduleBuilder) {}

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link com.intellij.openapi.application.PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overrided.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected final String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }
}
