package org.jetbrains.plugins.groovy.lang.generator;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.GeneratingCompiler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.06.2007
 */
public class GeneratorTest extends SimpleGroovyFileSetTestCase {
  protected static final String DATA_PATH = PathUtil.getDataPath(GeneratorTest.class);
  private VirtualFile myOutputDirVirtualFile;

  public GeneratorTest() {
    super(System.getProperty("path") != null ? System.getProperty("path") : DATA_PATH);
  }

  public String transform(String testName, String[] data) throws Exception {
    return "";
  }

  public String transformForRelPathTest(final String relTestPath, String[] data) throws Exception {
    final TempDirTestFixture tempDirFixture = new TempDirTestFixtureImpl();
    tempDirFixture.setUp();

    final StringBuffer buffer = new StringBuffer();
    try {
      final GroovyToJavaGeneratorTester groovyToJavaGeneratorTester = new GroovyToJavaGeneratorTester(relTestPath, data[0], myProject);
      final GeneratingCompiler.GenerationItem[][] generatedItems = new GeneratingCompiler.GenerationItem[1][1];

      GeneratingCompiler.GenerationItem[] generationItems = groovyToJavaGeneratorTester.getGenerationItems(null);

      myOutputDirVirtualFile = tempDirFixture.getFile("");

      generatedItems[0] = groovyToJavaGeneratorTester.generate(null, generationItems, myOutputDirVirtualFile);

      for (GeneratingCompiler.GenerationItem generatedItem : generatedItems[0]) {
        final String path = tempDirFixture.getTempDirPath() + File.separator + generatedItem.getPath();

        BufferedReader reader = new BufferedReader(new FileReader(path));
        int ch = reader.read();

        while (ch != -1) {
          buffer.append((char) ch);
          ch = reader.read();
        }
        reader.close();

        buffer.append("\n");
        buffer.append("---");
        buffer.append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      //System.out.println("----------" + relTestPath + "----------");
      //System.out.println(buffer);
      tempDirFixture.tearDown();
    }

    return buffer.toString();
  }

  class GroovyToJavaGeneratorTester extends GroovyToJavaGenerator {
    private String myRelTestPath;
    private String myFileContent;

    public GroovyToJavaGeneratorTester(String relTestPath, String fileContent, Project project) {
      super(project);
      myRelTestPath = relTestPath;
      myFileContent = fileContent;
    }

    protected VirtualFile[] getGroovyFilesToGenerate(CompileContext context) {
      try {
        final String testName = myRelTestPath.substring(0, myRelTestPath.lastIndexOf(".test"));
        PsiFile psiFile = TestUtils.createPseudoPhysicalFile(myProject, testName + ".groovy", myFileContent);
        return new VirtualFile[]{psiFile.getVirtualFile()};
      }
      catch (IncorrectOperationException e) {
      }

      return VirtualFile.EMPTY_ARRAY;
    }

    protected Module getModuleByFile(CompileContext context, VirtualFile file) {
      return myModule;
    }

    protected ProgressIndicator getProcessIndicator() {
      return null;
    }
  }

  protected IdeaProjectTestFixture createFixture() {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    JavaModuleFixtureBuilder fixtureBuilder = builder.addModule(JavaModuleFixtureBuilder.class).addJdk(TestUtils.getMockJdkHome());
    fixtureBuilder.addLibraryJars("GROOVY", TestUtils.getMockGrailsLibraryHome(), "groovy-all.jar");
    return builder.getFixture();
  }


  public static Test suite() {
    return new GeneratorTest();
  }

}
