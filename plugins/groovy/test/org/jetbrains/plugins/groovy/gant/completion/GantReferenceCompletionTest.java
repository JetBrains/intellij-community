package org.jetbrains.plugins.groovy.gant.completion;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.plugins.groovy.lang.completion.CompletionTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ilyas
 */
public class GantReferenceCompletionTest extends CompletionTestBase {

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/gant/completion";
  }

  @Override
  protected String getExtension() {
    return "gant";
  }

  public void testDep() throws Throwable { doTest(); }
  public void testInclude() throws Throwable { doTest(); }
  public void testAntJavacTarget() throws Throwable { doTest(); }
  public void testMutual() throws Throwable { doTest(); }
  public void testUnqual() throws Throwable { doTest(); }

  private static final String[] GANT_JARS = new String[]{"gant.jar", "ant.jar", "gant-junit.jar", "commons.jar"};

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibraryJars("GROOVY", TestUtils.getMockGrailsLibraryHome(), TestUtils.GROOVY_JAR);
    moduleBuilder.addLibraryJars("GANT", TestUtils.getTestDataPath() + "/mockGantLib", GANT_JARS);
  }

}