/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.overriding
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
public class FindOverridingMethodsAndClassesTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + "overriding/findOverridingMethodsAndClasses"
  }

  void testSimpleCase() {
    doTest(1, 1)
  }

  void testAnonymousClass() {
    doTest(2, 2)
  }

  void testTraitImplementsInterface() {
    doTest(4, 4)
  }

  void testClassExtendsTrait() {
    doTest(2, 2)
  }

  void testTraitExtendsTrait() { doTest 2, 2 }
  void testTraitImplementsTrait() { doTest 2, 2 }

  private void doTest(int methodCount, int classCount) {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    final GroovyFile groovyFile = (GroovyFile)myFixture.file
    final PsiClass psiClass = groovyFile.classes[0]
    final PsiMethod method = psiClass.methods[0]

    final Collection<PsiMethod> methods = OverridingMethodsSearch.search(method, psiClass.resolveScope, true).findAll()
    assertEquals("Method count is wrong", methodCount, methods.size())

    final Collection<PsiClass> classes = ClassInheritorsSearch.search(psiClass).findAll()
    assertEquals("Class count is wrong", classCount, classes.size())
  }

  public void "test find java functional expression passed into a groovy method"() {
    myFixture.addFileToProject("a.groovy", "interface I { void foo(); }; class C { static void bar(I i) {}}")
    myFixture.addFileToProject("a.java", "class D {{  C.bar(() -> {}); }")
    assertSize(1, FunctionalExpressionSearch.search(myFixture.findClass("I")).findAll());
  }
}
