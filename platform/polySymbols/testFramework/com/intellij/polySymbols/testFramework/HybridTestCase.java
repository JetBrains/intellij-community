// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Works like {@link BasePlatformTestCase}
 * or {@link CodeInsightFixtureTestCase}
 * depending on {@link #mode}
 *
 * @implNote
 * Cannot be converted to Kotlin because <code>myFixture</code> is used in Java and in Kotlin child classes as not-null.
 * There is no way to support both cases simultaneously without a massive child classes update.
 * For Kotlin, <code>lateinit</code> can be used, but the backing field is private and can't be used from Java.
 */
public abstract class HybridTestCase extends UsefulTestCase {

  protected HybridTestCase() {
    TestMode annotation = getClass().getAnnotation(TestMode.class);
    this.mode = annotation != null ? annotation.value() : HybridTestMode.BasePlatform;
  }

  protected HybridTestCase(@NotNull HybridTestMode mode) {
    this.mode = mode;
  }

  public final @NotNull HybridTestMode mode;

  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    if (mode == HybridTestMode.BasePlatform) {
      myFixture = createAndSetupLightFixture();
      myModule = myFixture.getModule();
    }
    else if (mode == HybridTestMode.CodeInsightFixture) {
      var fixtureAndModule = createAndSetupFullFixtureAndModule();
      myFixture = fixtureAndModule.first;
      myModule = fixtureAndModule.second;
    }
    else {
      throw new IllegalStateException(String.valueOf(mode));
    }
  }

  private CodeInsightTestFixture createAndSetupLightFixture() throws Exception {
    IdeaProjectTestFixture ideaFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createLightFixtureBuilder(getProjectDescriptor(), getTestName(false))
      .getFixture();

    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    TempDirTestFixture tmpFixture = policy != null ? policy.createTempDirTestFixture() : new LightTempDirTestFixtureImpl(true);

    CodeInsightTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createCodeInsightFixture(ideaFixture, tmpFixture);

    fixture.setTestDataPath(getTestDataPath());
    fixture.setUp();

    return fixture;
  }

  private Pair<CodeInsightTestFixture, Module> createAndSetupFullFixtureAndModule() throws Exception {
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
      .createFixtureBuilder(getFixtureProjectName());

    CodeInsightTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createCodeInsightFixture(projectBuilder.getFixture());

    ModuleFixtureBuilder<?> moduleFixtureBuilder = projectBuilder.addModule(getModuleBuilderClass());
    moduleFixtureBuilder.addSourceContentRoot(fixture.getTempDirPath());

    fixture.setTestDataPath(getTestDataPath());
    fixture.setUp();

    return new Pair<>(fixture, moduleFixtureBuilder.getFixture().getModule());
  }

  private static final int MAX_PROJECT_NAME_LENGTH = 20;

  private String getFixtureProjectName() {
    // Long paths may be a problem on Windows
    String testName = getTestName(false);
    return testName.length() > MAX_PROJECT_NAME_LENGTH ? testName.substring(0, MAX_PROJECT_NAME_LENGTH) : testName;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
    // Because client tests are JUnit3, @Rule won't work -- wrapping execution manually
    ProgressManager.getInstance().executeProcessUnderProgress(
      () -> {
        try {
          super.runTestRunnable(testRunnable);
        }
        catch (AssumptionViolatedException | AssertionError e) {
          throw e;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      },
      new DaemonProgressIndicator()
    );
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myFixture != null) {
        myFixture.tearDown();
      }
      myFixture = null;
      myModule = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * @see BasePlatformTestCase#getTestDataPath()
   */
  protected abstract String getTestDataPath();

  /**
   * @see BasePlatformTestCase#getProjectDescriptor()
   */
  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

  /**
   * @see CodeInsightFixtureTestCase#getModuleBuilderClass()
   */
  protected Class<? extends ModuleFixtureBuilder<?>> getModuleBuilderClass() {
    //noinspection unchecked,rawtypes
    return (Class)EmptyModuleFixtureBuilder.class;
  }

  /**
   * @see BasePlatformTestCase#getTestRootDisposable()
   */
  @Override
  public @NotNull Disposable getTestRootDisposable() {
    return myFixture == null ? super.getTestRootDisposable() : myFixture.getTestRootDisposable();
  }

  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myFixture.getProject();
  }

  @ApiStatus.Internal
  public boolean isOpenedInMyEditor(@NotNull VirtualFile virtualFile) {
    return myFixture.isOpenedInMyEditor(virtualFile);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface TestMode {
    @NotNull HybridTestMode value();
  }
}
