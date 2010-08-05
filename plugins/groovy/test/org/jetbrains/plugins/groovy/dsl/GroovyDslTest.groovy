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
package org.jetbrains.plugins.groovy.dsl;


import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.psi.PsiClass

/**
 * @author peter
 */
public class GroovyDslTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new DefaultLightProjectDescriptor() {
    @Override def void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      PsiTestUtil.addLibrary(module, model, "GROOVY", TestUtils.getMockGroovyLibraryHome(), TestUtils.GROOVY_JAR);
    }
  }

  @Override
  protected String getBasePath() {
    TestUtils.getTestDataPath() + "groovy/dsl"
  }

  private def doCustomTest(String s) {
    final PsiFile file = myFixture.addFileToProject(getTestName(false) + "Enhancer.gdsl", s);
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  public void doTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".gdsl", getTestName(false) + "_after.gdsl")
  }

  public void doPlainTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  public void testCompleteMethod() throws Throwable { doTest() }

  public void testCompleteProperty() throws Throwable { doTest() }

  public void testCompleteClassMethod() throws Throwable {
    doCustomTest("""
      def ctx = context(ctype: "java.lang.String")

      contributor ([ctx], {
        method name:"zzz", type:"void", params:[:]
      })
""")
  }

  public void testDelegateToThrowable() throws Throwable {
    doCustomTest("""
      def ctx = context(ctype: "java.lang.String")

      contributor ctx, {
        findClass("java.lang.Throwable")?.methods?.each{add it}
      }
""")
  }

  public void testDelegateToArgument() throws Throwable {
    doCustomTest("""
      def ctx = context(scope: closureScope(isArgument: true))

      contributor(ctx, {
        def call = enclosingCall("boo")
        if (call) {
          def method = call.bind()
          if ("Runner".equals(method?.containingClass?.qualifiedName)) {
            delegatesTo(call.arguments[0]?.classType)
          }
        }
      })
""")
  }

  public void testDelegateToArgument2() throws Throwable {
    doCustomTest("""
      def ctx = context(scope: closureScope(isArgument: true))

      contributor(ctx, {
        def call = enclosingCall("boo")
        if (call) {
          delegatesTo(call.arguments[0]?.classType)
        }
      })
""")
  }

  public void testClassContext() throws Throwable {
    doCustomTest( """
     def ctx = context(scope: classScope(name: /.*WsSecurityConfig/))
     
     contributor ctx, {
       property name: "auxWsProperty", type: "java.lang.String"
     }
    """
    )
  }

  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  public void testCategoryWhenMethodRenamed() {
    PsiClass category = myFixture.addClass("""
public class MyCategory {
  public void foo(String s) {}
}""")
    def foo = category.getMethods()[0]
    final PsiFile file = myFixture.addFileToProject(getTestName(false) + "Enhancer.gdsl", """
category 'MyCategory'""");
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)
    myFixture.renameElement foo, "bar", false, false

    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }
}
