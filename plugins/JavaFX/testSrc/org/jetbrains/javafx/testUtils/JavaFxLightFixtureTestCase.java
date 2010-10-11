package org.jetbrains.javafx.testUtils;

import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
abstract public class JavaFxLightFixtureTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private static boolean ourPlatformPrefixInitialized;
  private LightProjectDescriptor myProjectDescriptor = new JavaFxLightProjectDescriptor();

  protected static class JavaFxLightProjectDescriptor implements LightProjectDescriptor {
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    public Sdk getSdk() {
      return null;
    }

    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initPlatformPrefix();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myFixture.setTestDataPath(getTestDataPath());

    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  public LightProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }

  public static String getTestDataPath() {
    return JavaFxTestUtil.getTestDataPath();
  }

  public static void initPlatformPrefix() {
    if (!ourPlatformPrefixInitialized) {
      ourPlatformPrefixInitialized = true;
      boolean isIDEA = true;
      try {
        JavaFxLightFixtureTestCase.class.getClassLoader().loadClass("com.intellij.openapi.project.impl.IdeaProjectManagerImpl");
      }
      catch (ClassNotFoundException e) {
        isIDEA = false;
      }
      if (!isIDEA) {
        System.setProperty("idea.platform.prefix", "JavaFx");
      }
    }
  }
}
