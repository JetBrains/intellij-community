package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.06.2007
 */
public class GeneratorTest extends LightGroovyTestCase {

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

  public void doTest() throws Exception {
    final String relTestPath = getTestName(true) + ".test";
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + relTestPath);

    final StringBuffer buffer = new StringBuffer();
    final String testName = StringUtil.trimEnd(relTestPath, ".test");
    PsiFile psiFile = TestUtils.createPseudoPhysicalFile(getProject(), testName + ".groovy", data.get(0));
    final Map<String,String> map =
      new GroovyToJavaGenerator(getProject(), null, Arrays.asList(psiFile.getViewProvider().getVirtualFile()))
        .generateStubs((GroovyFile)psiFile);

     for (String stubText : map.values()) {
      buffer.append(stubText);
      buffer.append("\n");
      buffer.append("---");
      buffer.append("\n");
    }

    assertEquals(data.get(1).trim(), buffer.toString().trim());
  }

}
