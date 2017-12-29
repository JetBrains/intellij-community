/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * User: Dmitry.Krasilschikov
 */
class ImplementsExtendsQuickFixTest extends LightCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/extendsImplementsFix/"

  void testClass1() { doTest() }

  void testExt1() { doTest() }

  void testImpl1() { doTest() }

  void testImpl2() { doTest() }

  void testImplext1() { doTest() }

  void testImplExt2() { doTest() }

  void testInterface1() { doTest() }


  void doTest() {
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

        WriteCommandAction.runWriteCommandAction project, {
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
