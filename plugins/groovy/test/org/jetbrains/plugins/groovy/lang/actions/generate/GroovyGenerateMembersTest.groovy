/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.actions.generate;


import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.RunResult
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.actions.generate.constructors.ConstructorGenerateHandler
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.codeInsight.generation.PsiMethodMember

/**
 * @author peter
 */
public class GroovyGenerateMembersTest extends LightCodeInsightFixtureTestCase {

  public void testConstructorAtOffset() throws Throwable {
    doTest();
  }

  public void testConstructorAtEnd() throws Throwable {
    doTest();
  }
  
  public void testLonelyConstructor() throws Throwable {
    doTest();
  }

  public void testExplicitArgumentTypes() throws Exception {
    myFixture.configureByText("a.groovy", """
class Super {
  def Super(a, int b) {}
}

class Foo extends Super {
  int c
  Object d
  final e
  <caret>
}
""")
    generateConstructor()
    myFixture.checkResult """
class Super {
  def Super(a, int b) {}
}

class Foo extends Super {
  int c
  Object d
  final e

  def Foo(a, int b, int c, Object d, e) {
    super(a, b)
    this.c = c
    this.d = d
    this.e = e
  }
}
"""
  }

  private void doTest() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    generateConstructor();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  RunResult generateConstructor() {
    return new WriteCommandAction(getProject(), new PsiFile[0]) {
      protected void run(Result result) throws Throwable {
        new ConstructorGenerateHandler() {
          @Override protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
            List<ClassMember> members = aClass.fields.collect { new PsiFieldMember(it) }
            members << new PsiMethodMember(aClass.superClass.constructors[0])
            return members as ClassMember[]
          }

        }.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute()
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "generate";
  }
}
