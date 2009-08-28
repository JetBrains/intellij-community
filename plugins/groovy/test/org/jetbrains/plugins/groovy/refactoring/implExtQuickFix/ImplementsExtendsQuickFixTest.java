package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.10.2007
 */
public class ImplementsExtendsQuickFixTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/extendsImplementsFix/";
  }

  public void testClass1() throws Throwable { doTest(); }
  public void testExt1() throws Throwable { doTest(); }
  public void testImpl1() throws Throwable { doTest(); }
  public void testImpl2() throws Throwable { doTest(); }
  public void testImplext1() throws Throwable { doTest(); }
  public void testImplExt2() throws Throwable { doTest(); }
  public void testInterface1() throws Throwable { doTest(); }



    public void doTest() throws Exception {
      final List<String> data = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + getTestName(true) + ".test");
      String fileText = data.get(0);
      final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), fileText);
      assert psiFile instanceof GroovyFileBase;
      final GrTypeDefinition[] typeDefinitions = ((GroovyFileBase) psiFile).getTypeDefinitions();
      final GrTypeDefinition typeDefinition = typeDefinitions[typeDefinitions.length - 1];
      String newText;
      if (typeDefinition.getImplementsClause() == null && typeDefinition.getExtendsClause() == null) {
        newText = "";
      } else {
        new WriteCommandAction(getProject()) {
          protected void run(Result result) throws Throwable {
            ChangeExtendsImplementsQuickFix fix =
              new ChangeExtendsImplementsQuickFix(typeDefinition.getExtendsClause(), typeDefinition.getImplementsClause());
            fix.invoke(getProject(), null, psiFile);
          }
        }.execute();

        final GrTypeDefinition[] newTypeDefinitions = ((GroovyFileBase) psiFile).getTypeDefinitions();
        newText = newTypeDefinitions[newTypeDefinitions.length - 1].getText();
      }
      assertEquals(data.get(1), newText);
    }

}
