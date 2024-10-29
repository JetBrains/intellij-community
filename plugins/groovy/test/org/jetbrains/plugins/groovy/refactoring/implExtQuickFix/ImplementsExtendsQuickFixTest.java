// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 */
public class ImplementsExtendsQuickFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testClass1() { doTest(); }

  public void testExt1() { doTest(); }

  public void testImpl1() { doTest(); }

  public void testImpl2() { doTest(); }

  public void testImplext1() { doTest(); }

  public void testImplExt2() { doTest(); }

  public void testInterface1() { doTest(); }

  public void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    String fileText = data.get(0);
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), fileText);
    assert psiFile instanceof GroovyFileBase;
    final GrTypeDefinition[] typeDefinitions = ((GroovyFileBase)psiFile).getTypeDefinitions();
    final GrTypeDefinition typeDefinition = typeDefinitions[typeDefinitions.length - 1];
    String newText;
    if (typeDefinition.getImplementsClause() == null && typeDefinition.getExtendsClause() == null) {
      newText = "";
    }
    else {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          ChangeExtendsImplementsQuickFix fix = new ChangeExtendsImplementsQuickFix(typeDefinition);
          fix.asIntention().invoke(getProject(), null, psiFile);
          UsefulTestCase.doPostponedFormatting(getProject());
        });

      final GrTypeDefinition[] newTypeDefinitions = ((GroovyFileBase)psiFile).getTypeDefinitions();
      newText = newTypeDefinitions[newTypeDefinitions.length - 1].getText();
    }

    TestCase.assertEquals(data.get(1), newText);
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/extendsImplementsFix/";
  }
}
