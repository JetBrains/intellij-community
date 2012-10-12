package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * User: Dmitry.Krasilschikov
 * Date: 11.10.2007
 */
public class ImplementsExtendsQuickFixTest extends LightCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/extendsImplementsFix/"

  public void testClass1() { doTest() }
  public void testExt1() { doTest() }
  public void testImpl1() { doTest() }
  public void testImpl2() { doTest() }
  public void testImplext1() { doTest() }
  public void testImplExt2() { doTest() }
  public void testInterface1() { doTest() }



    public void doTest() {
      final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
      String fileText = data.get(0)
      final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(project, fileText)
      assert psiFile instanceof GroovyFileBase
      final GrTypeDefinition[] typeDefinitions = ((GroovyFileBase)psiFile).typeDefinitions
      final GrTypeDefinition typeDefinition = typeDefinitions[typeDefinitions.length - 1]
      String newText
      if (typeDefinition.implementsClause == null && typeDefinition.extendsClause == null) {
        newText = ""
      }
      else {

        ApplicationManager.application.runWriteAction {
          ChangeExtendsImplementsQuickFix fix = new ChangeExtendsImplementsQuickFix(typeDefinition)
          fix.invoke(project, null, psiFile)
          doPostponedFormatting(project)
        } as Runnable

        final GrTypeDefinition[] newTypeDefinitions = ((GroovyFileBase)psiFile).typeDefinitions
        newText = newTypeDefinitions[newTypeDefinitions.length - 1].text
      }
      assertEquals(data.get(1), newText)
    }

}
