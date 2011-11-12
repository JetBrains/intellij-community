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


import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider
import org.jetbrains.plugins.groovy.util.TestUtils

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
    addGdsl(s)
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  private def addGdsl(String text) {
    final PsiFile file = myFixture.addFileToProject(getTestName(false) + "Enhancer.gdsl", text);
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)
  }

  public void doTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".gdsl", getTestName(false) + "_after.gdsl")
  }

  public void doPlainTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  public void testCompleteTopLevel() throws Throwable {
    myFixture.configureByText 'a.gdsl', '<caret>'
    myFixture.completeBasic()
    def expected = ['contributor', 'contribute', 'currentType', 'assertVersion']
    if (!myFixture.lookupElementStrings.containsAll(expected)) {
      assertSameElements(expected, myFixture.lookupElementStrings)
    }
  }

  public void testCompleteInContributor() throws Throwable {
    myFixture.configureByText 'a.gdsl', 'contribute { <caret> }'
    myFixture.completeBasic()
    def expected = ['method', 'property', 'parameter']
    if (!myFixture.lookupElementStrings.containsAll(expected)) {
      assertSameElements(expected, myFixture.lookupElementStrings)
    }
  }

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

  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  public void testCategoryWhenMethodRenamed() {
    PsiClass category = myFixture.addClass("""
public class MyCategory {
  public void foo(String s) {}
}""")
    def foo = category.getMethods()[0]
    addGdsl("""
    contributor([:]){category 'MyCategory'}""")
    myFixture.renameElement foo, "bar", false, false

    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  public void testPathRegexp() {
    addGdsl "contributor(pathRegexp: '.*aaa.*') { property name:'fffooo', type:'int' }"

    myFixture.configureFromExistingVirtualFile myFixture.addFileToProject("aaa/foo.groovy", "fff<caret>x").virtualFile
    myFixture.completeBasic()
    assertOrderedEquals myFixture.lookupElementStrings, 'fffooo'

    myFixture.configureFromExistingVirtualFile myFixture.addFileToProject("bbb/foo.groovy", "fff<caret>x").virtualFile
    myFixture.completeBasic()
    assertEmpty myFixture.lookupElementStrings
  }

  public void testNamedParameters() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[:], namedParams:[
    parameter(name:'param1', type:String),
    parameter(name:'param2', type:Integer),
  ]
}'''
    myFixture.configureByText 'a.groovy', '"".foo(par<caret>)'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['param1', 'param2']
  }

  public void testNamedParametersGroovyConvention() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[args:[
      parameter(name:'param1', type:String, doc:'My doc'),
      parameter(name:'param2', type:Integer),
    ]]
}'''
    myFixture.configureByText 'a.groovy', '"".foo(par<caret>)'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['param1', 'param2']
    assert '<pre><b>param1</b>: java.lang.String</pre><p>My doc' == generateDoc()
  }

  private String generateDoc() {
    def element = DocumentationManager.getInstance(project).getElementFromLookup(myFixture.editor, myFixture.file)
    return new GroovyDocumentationProvider().generateDoc(element, null)
  }

  public void testCheckNamedArgumentTypes() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[args:[
      parameter(name:'param1', type:File),
      parameter(name:'param2', type:Integer),
    ]]
}'''
    myFixture.configureByText 'a.groovy', '''
"".foo(param1:<warning descr="Type of argument 'param1' can not be 'Integer'">2</warning>, param2:2)
'''
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection())
    myFixture.checkHighlighting(true, false, false)
  }

  public void testMethodDoc() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[:], doc:'Some doc'
}'''
    myFixture.configureByText 'a.groovy', '"".fo<caret>o'
    myFixture.completeBasic()
    assert generateDoc().contains('Some doc')
    assert generateDoc().contains('foo')
    assert generateDoc().contains('()')
  }

  public void testPropertyDoc() {
    addGdsl '''contribute(currentType(String.name)) {
  property name:'foo', type:int, doc:'Some doc2'
}'''
    myFixture.configureByText 'a.groovy', '"".fo<caret>o'
    myFixture.completeBasic()
    assert generateDoc().contains('Some doc2')
    assert generateDoc().contains('foo')
  }
}
