/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;


import com.intellij.lang.StdLanguages
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
public class CodeBlockGenerationTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel;
      final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "refactoring/convertGroovyToJava/codeBlock";
  }

  private void doTest() {
    final String testName = getTestName(true)
    final PsiFile file = myFixture.configureByFile(testName + ".groovy");
    assertInstanceOf file, GroovyFile

    GrTopStatement[] statements = file.topStatements
    final StringBuilder builder = new StringBuilder()
    def generator = new CodeBlockGenerator(builder, new ExpressionContext(project));
    for (def statement: statements) {
      statement.accept(generator);
      builder.append("\n")
    }

    final PsiFile result = createLightFile(testName + ".java", StdLanguages.JAVA, builder.toString())
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    final String text = result.text
    final String expected = psiManager.findFile(myFixture.copyFileToProject(testName + ".java")).text
    assertEquals expected, text
  }

  private addFile(String text) {
    myFixture.addFileToProject("Bar.groovy", text)
  }

  void testSwitch1() {doTest()}
  void testSwitch2() {doTest()}
  void testSwitch3() {doTest()}
  void testSwitch4() {doTest()}

  void _testWhile1() {doTest()}
  void _testWhile2() {doTest()}
  void _testWhile3() {doTest()}

  void testRefExpr() {
    myFixture.addFileToProject "Bar.groovy", """
class Bar {
  def foo = 2

  def getBar() {3}
}
class MyCat {
  static getAbc(Bar b) {
    return 4
  }
}
"""

    doTest()
  }

  void testMemberPointer() {doTest()}

  void testCompareMethods() {
    addFile """
class Bar {
  def compareTo(def other) {1}
}"""
  }
}
