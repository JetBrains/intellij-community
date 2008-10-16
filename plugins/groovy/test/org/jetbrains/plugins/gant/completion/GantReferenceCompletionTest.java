package org.jetbrains.plugins.gant.completion;

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.CompletionTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ilyas
 */
public class GantReferenceCompletionTest extends CompletionTestBase {

  @NonNls
  private static final String DATA_PATH = PathManager.getHomePath() + "/svnPlugins/groovy/test/org/jetbrains/plugins/gant/completion/data";
  public static final String TEMP_FILE = "temp.gant";

  private static final String[] GANT_JARS = new String[]{"gant.jar", "ant.jar", "gant-junit.jar", "commons.jar"};

  public GantReferenceCompletionTest() {
    super(System.getProperty("path") != null ? System.getProperty("path") : DATA_PATH);
  }

  protected PsiFile createFile(String fileText) throws IncorrectOperationException {
    return TestUtils.createPseudoPhysicalFile(myProject, TEMP_FILE, fileText);
  }

  protected boolean addKeywords(PsiReference ref) {
    return false;
  }

  protected boolean addReferenceVariants() {
    return true;
  }

  public static Test suite() {
    return new GantReferenceCompletionTest();
  }

  protected IdeaProjectTestFixture createFixture() {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    JavaModuleFixtureBuilder fixtureBuilder = builder.addModule(JavaModuleFixtureBuilder.class);
    fixtureBuilder.addLibraryJars("GROOVY", TestUtils.getMockGrailsLibraryHome(), TestUtils.GROOVY_JAR);
    fixtureBuilder.addLibraryJars("GANT", getMockGantLibraryHome(), GANT_JARS);
    return builder.getFixture();
  }

  public static String getMockGantLibraryHome() {
    return TestUtils.getTestDataPath() + "/mockGantLib";
  }
}