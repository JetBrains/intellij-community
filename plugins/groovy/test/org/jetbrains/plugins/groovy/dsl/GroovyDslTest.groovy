/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl

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
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
@CompileStatic
class GroovyDslTest extends LightCodeInsightFixtureTestCase {
  private static LightProjectDescriptor descriptor = new DefaultLightProjectDescriptor() {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      PsiTestUtil.addLibrary(module, model, "GROOVY", TestUtils.getMockGroovyLibraryHome(), TestUtils.GROOVY_JAR)
    }
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    descriptor
  }


  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + "groovy/dsl"
  }

  private def doCustomTest(String s) {
    addGdsl(s)
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  private def addGdsl(String text) {
    final PsiFile file = myFixture.addFileToProject(getTestName(false) + "Enhancer.gdsl", text)
    GroovyDslFileIndex.activate(file.virtualFile)
  }

  void doTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".gdsl", getTestName(false) + "_after.gdsl")
  }

  void testCompleteTopLevel() throws Throwable {
    myFixture.configureByText 'a.gdsl', '<caret>'
    myFixture.completeBasic()
    def expected = ['contributor', 'contribute', 'currentType', 'assertVersion']
    if (!myFixture.lookupElementStrings.containsAll(expected)) {
      assertSameElements(expected, myFixture.lookupElementStrings)
    }
  }

  void testCompleteInContributor() throws Throwable {
    myFixture.configureByText 'a.gdsl', 'contribute { <caret> }'
    myFixture.completeBasic()
    def expected = ['method', 'property', 'parameter']
    if (!myFixture.lookupElementStrings.containsAll(expected)) {
      assertSameElements(expected, myFixture.lookupElementStrings)
    }
  }

  void testCompleteClassMethod() throws Throwable {
    doCustomTest("""
      def ctx = context(ctype: "java.lang.String")

      contributor ([ctx], {
        method name:"zzz", type:"void", params:[:]
      })
""")
  }

  void "test on anonymous class"() {
    addGdsl '''
import com.intellij.patterns.PsiJavaPatterns

contribute(ctype:PsiJavaPatterns.psiClass()) {
  method name:'foo' + psiClass.name, type:void, params:[:]
}'''
    myFixture.configureByText 'a.groovy', '''
class Foo<T> {
  def foo(T t) { 
    t.f<caret>
  }  
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'fooT', 'finalize'
  }

  void testDelegateToThrowable() throws Throwable {
    doCustomTest("""
      def ctx = context(ctype: "java.lang.String")

      contributor ctx, {
        findClass("java.lang.Throwable")?.methods?.each{add it}
      }
""")
  }

  void testDelegateToArgument() throws Throwable {
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

  void testDelegateToArgument2() throws Throwable {
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

  void testClassContext() throws Throwable {
    addGdsl("""
     def ctx = context(scope: classScope(name: /.*WsSecurityConfig/))
     
     contributor ctx, {
       property name: "auxWsProperty", type: "java.lang.String"
     }
    """)
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", '\n', getTestName(false) + "_after.groovy")
  }

  void testCategoryWhenMethodRenamed() {
    PsiClass category = myFixture.addClass("""
public class MyCategory {
  public static void foo(String s) {}
}""")
    def foo = TestUtils.getMethods(category)[0]
    addGdsl("""
    contributor([:]){category 'MyCategory'}""")
    myFixture.renameElement foo, "bar", false, false

    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy")
  }

  void testPathRegexp() {
    addGdsl "contributor(pathRegexp: '.*aaa.*') { property name:'fffooo', type:'int' }"

    myFixture.configureFromExistingVirtualFile myFixture.addFileToProject("aaa/foo.groovy", "fff<caret>x").virtualFile
    myFixture.completeBasic()
    assert 'fffooo' in myFixture.lookupElementStrings

    myFixture.configureFromExistingVirtualFile myFixture.addFileToProject("bbb/foo.groovy", "fff<caret>x").virtualFile
    myFixture.completeBasic()
    assertEmpty myFixture.lookupElementStrings
  }

  void testNamedParameters() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[:], namedParams:[
    parameter(name:'param1', type:String),
    parameter(name:'param2', type:Integer),
  ]
}'''
    myFixture.configureByText 'a.groovy', '"".foo(par<caret>)'
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param1', 'param2'
  }

  void testNamedParametersGroovyConvention() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[args:[
      parameter(name:'param1', type:String, doc:'My doc'),
      parameter(name:'param2', type:Integer),
    ]]
}'''
    myFixture.configureByText 'a.groovy', '"".foo(par<caret>)'
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param1', 'param2'
    assert '<pre><b>param1</b>: java.lang.String</pre><p>My doc' == generateDoc()
  }

  private String generateDoc() {
    def element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    return new GroovyDocumentationProvider().generateDoc(element, null)
  }

  void testCheckNamedArgumentTypes() {
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

  void testMethodDoc() {
    addGdsl '''contribute(currentType(String.name)) {
  method name:'foo', type:void, params:[:], doc:'Some doc'
}'''
    myFixture.configureByText 'a.groovy', '"".fo<caret>o'
    myFixture.completeBasic()
    assert generateDoc().contains('Some doc')
    assert generateDoc().contains('foo')
    assert generateDoc().contains('()')
  }

  void testPropertyDoc() {
    addGdsl '''contribute(currentType(String.name)) {
  property name:'foo', type:int, doc:'Some doc2'
}'''
    myFixture.configureByText 'a.groovy', '"".fo<caret>o'
    myFixture.completeBasic()
    assert generateDoc().contains('Some doc2')
    assert generateDoc().contains('getFoo')
  }

  void testVariableInAnnotationClosureContext() {
    addGdsl '''
      contributor(scope: closureScope(annotationName:'Ensures')) {
        variable(name: 'result', type:'java.lang.Object')
      }
    '''

    myFixture.configureByText('a.groovy', '''\
      @interface Ensures {}

      @Ensures({re<caret>sult == 2})
      def foo() {}
    ''')

    assertNotNull(myFixture.getReferenceAtCaretPosition().resolve())
  }

  void testVariableInMethodCallClosureContext() {
    addGdsl '''
      contributor(scope: closureScope(methodName:'ensures')) {
        variable(name: 'result', type:'java.lang.Object')
      }
    '''

    myFixture.configureByText('a.groovy', '''\
      ensures{re<caret>sult == 2}
    ''')

    assertNotNull(myFixture.getReferenceAtCaretPosition().resolve())
  }

  void testScriptSuperClass() {
    myFixture.addClass('''\
      public class Abc {
        public void foo() {}
      }
    ''')
    addGdsl('''
      scriptSuperClass(pattern: 'a.groovy', superClass: 'Abc')
    ''')

    myFixture.configureByText('a.groovy', '''\
      fo<caret>o()
    ''')

    assertNotNull(myFixture.getReferenceAtCaretPosition().resolve())
  }

  void testEnumConstructor() {
    myFixture.configureByText('a.groovy', '''\
enum Myenum {
    a<caret>b(1, 2, 4)
}
''')

    addGdsl('''
contribute(currentType("Myenum")) {
    constructor params:[foo:Integer, bar:Integer, goo:Integer]
}''')
    assertNotNull(myFixture.getReferenceAtCaretPosition().resolve())
  }

  void 'test complete base script members'() {
    myFixture.with {
      addFileToProject 'pckg/MyBaseScriptClass.groovy', '''
package pckg

abstract class MyBaseScriptClass extends Script {
    def foo() {}
    def prop
}
'''
      addGdsl '''
scriptSuperClass(pattern: 'a.groovy', superClass: 'pckg.MyBaseScriptClass')
'''
      configureByText 'a.groovy', '<caret>'
      completeBasic()
      lookupElementStrings.with {
        assert 'foo' in it
        assert 'prop' in it
      }
    }
  }
}
