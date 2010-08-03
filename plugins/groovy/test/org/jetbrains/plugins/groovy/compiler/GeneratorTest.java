package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.06.2007
 */
public class GeneratorTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/stubGenerator";
  }

public void testArrayType1() throws Throwable { doTest(); }
  public void testAtInterface() throws Throwable { doTest(); }
  public void testDefInInterface() throws Throwable { doTest(); }
  public void testExtends1() throws Throwable { doTest(); }
  public void testExtendsImplements() throws Throwable { doTest(); }
  public void testGetterAlreadyDefined() throws Throwable { doTest(); }
  public void testGrvy1098() throws Throwable { doTest(); }
  public void testGrvy118() throws Throwable { doTest(); }
  public void testGrvy1358() throws Throwable { doTest(); }
  public void testGrvy1376() throws Throwable { doTest(); }
  public void testGrvy170() throws Throwable { doTest(); }
  public void testGrvy903() throws Throwable { doTest(); }
  public void testGrvy908() throws Throwable { doTest(); }
  public void testGRVY915() throws Throwable { doTest(); }
  public void testImplements1() throws Throwable { doTest(); }
  public void testKireyev() throws Throwable { doTest(); }
  public void testMethodTypeParameters() throws Throwable { doTest(); }
  public void testOptionalParameter() throws Throwable { doTest(); }
  public void testOverrideFinalGetter() throws Throwable { doTest(); }
  public void testPackage1() throws Throwable { doTest(); }
  public void testScript() throws Throwable { doTest(); }
  public void testSetterAlreadyDefined1() throws Throwable { doTest(); }
  public void testSetUpper1() throws Throwable { doTest(); }
  public void testSingletonConstructor() throws Throwable { doTest(); }
  public void testStringMethodName() throws Throwable { doTest(); }
  public void testSuperInvocation() throws Throwable { doTest(); }
  public void testSuperInvocation1() throws Throwable { doTest(); }
  public void testToGenerate() throws Throwable { doTest(); }
  public void testToGenerate1() throws Throwable { doTest(); }
  public void testVararg1() throws Throwable { doTest(); }
  public void testInaccessibleConstructor() throws Throwable { doTest(); }
  public void testSynchronizedProperty() throws Throwable { doTest(); }
  public void testVarargs() throws Throwable { doTest(); }

  public void testCheckedExceptionInConstructorDelegate() throws Throwable {
    myFixture.addClass("package foo;" +
                       "public class SuperClass {" +
                       "  public SuperClass(String s) throws java.io.IOException {}" +
                       "}");
    doTest();
  }

  public void testInaccessiblePropertyType() throws Throwable {
    myFixture.addClass("package foo;" +
                       "class Hidden {}");
    doTest();
  }

  public void testImmutableAnno() throws Throwable {
    myFixture.addClass("package groovy.lang; public @interface Immutable {}");
    doTest();
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibraryJars("GROOVY", TestUtils.getMockGroovyLibraryHome(), "groovy-all.jar");
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
  }

  public void doTest() throws Exception {


    final String relTestPath = getTestName(true) + ".test";
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + relTestPath);

    final TempDirTestFixture tempDirFixture = myFixture.getTempDirFixture();

    final StringBuffer buffer = new StringBuffer();
    final GroovyToJavaGeneratorTester groovyToJavaGeneratorTester = new GroovyToJavaGeneratorTester(relTestPath, data.get(0), getProject());
    final GroovyToJavaGenerator.GenerationItem[][] generatedItems = new GroovyToJavaGenerator.GenerationItem[1][1];

    GroovyToJavaGenerator.GenerationItem[] generationItems = groovyToJavaGeneratorTester.getGenerationItems(null);

    VirtualFile outputDirVirtualFile = tempDirFixture.getFile("");

    generatedItems[0] = groovyToJavaGeneratorTester.generate(generationItems, outputDirVirtualFile);

    for (GroovyToJavaGenerator.GenerationItem generatedItem : generatedItems[0]) {
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

    assertEquals(data.get(1).trim(), buffer.toString().trim());
  }

  class GroovyToJavaGeneratorTester extends GroovyToJavaGenerator {
    private final String myRelTestPath;
    private final String myFileContent;

    public GroovyToJavaGeneratorTester(String relTestPath, String fileContent, Project project) {
      super(project, null);
      myRelTestPath = relTestPath;
      myFileContent = fileContent;
    }

    protected VirtualFile[] getGroovyFilesToGenerate(CompileContext context) {
      try {
        final String testName = myRelTestPath.substring(0, myRelTestPath.lastIndexOf(".test"));
        PsiFile psiFile = TestUtils.createPseudoPhysicalFile(getProject(), testName + ".groovy", myFileContent);
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


}
