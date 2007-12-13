package org.jetbrains.plugins.groovy.lang.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.GeneratingCompiler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
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
  protected static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/generator/data/";
  protected static final File OUTPUT_DIR = new File("test/org/jetbrains/plugins/groovy/lang/generator/output/");
  private final Object LOCK = new Object();
  private int mySemaphore = 0;

  public GeneratorTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  public void up() {
    synchronized (LOCK) {
      mySemaphore++;
      LOCK.notifyAll();
    }
  }

  public void down() {
    synchronized (LOCK) {
      mySemaphore--;
      LOCK.notifyAll();
    }
  }

  public void waitFor() throws Exception {
    synchronized (LOCK) {
      if (mySemaphore > 0) {
        LOCK.wait();
      }
    }
  }

  public String transform(String testName, String[] data) throws Exception {
    return "";
  }

  public String transformForRelPathTest(final String relTestPath, String[] data) throws Exception {
    final GroovyToJavaGeneratorTester groovyToJavaGeneratorTester = new GroovyToJavaGeneratorTester(relTestPath, data[0], myProject);
    final GeneratingCompiler.GenerationItem[][] generatedItems = new GeneratingCompiler.GenerationItem[1][1];

    final StringBuffer buffer = new StringBuffer();
    final Runnable generateThread = new Runnable() {
      public void run() {
        GeneratingCompiler.GenerationItem[] generationItems = groovyToJavaGeneratorTester.getGenerationItems(null);

        VirtualFile outputDirVirtualFile;
        try {
          outputDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(OUTPUT_DIR.getCanonicalPath().replace(File.separatorChar, '/')));

          generatedItems[0] = groovyToJavaGeneratorTester.generate(null, generationItems, outputDirVirtualFile);

          for (GeneratingCompiler.GenerationItem generatedItem : generatedItems[0]) {
            String path = OUTPUT_DIR + File.separator + generatedItem.getPath();

            BufferedReader reader = new BufferedReader(new FileReader(path));
            int ch = reader.read();

            while (ch != -1) {
              buffer.append((char) ch);
              ch = reader.read();
            }

            buffer.append("\n");
            buffer.append("---");
            buffer.append("\n");
          }
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          System.out.println("----------" + relTestPath + "----------");
          System.out.println(buffer);
          down();
        }
      }
    };

    up();
    ApplicationManager.getApplication().executeOnPooledThread(generateThread);

    waitFor();
    Thread.sleep(5);

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
      } catch (IncorrectOperationException e) {
      }

      return VirtualFile.EMPTY_ARRAY;
    }

    protected Module getModuleByFile(CompileContext context, VirtualFile file) {
      return module;
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
