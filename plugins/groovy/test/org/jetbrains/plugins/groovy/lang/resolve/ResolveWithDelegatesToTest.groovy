/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.LightGroovyTestCase

/**
 * @author Max Medvedev
 */
class ResolveWithDelegatesToTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() { null }

  void testShouldChooseMethodFromOwner() {
    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }
                int doIt(@DelegatesTo(Delegate) Closure cl) {
                    cl.delegate = new Delegate()
                    cl() as int
                }
                int test() {
                    doIt {
                        @ASTTest(phase=INSTRUCTION_SELECTION, value={
                            node = node.rightExpression
                            def target = node.getNodeMetaData(DIRECT_METHOD_CALL_TARGET)
                            assert target != null
                            assert target.declaringClass.name == 'Owner'
                        })
                        def x = fo<caret>o() // as the delegation strategy is owner first, should return 1
                        x
                    }
                }
            }
            def o = new Owner()
            assert o.test() == 1
        ''', 'Owner'
  }

  void testShouldChooseMethodFromDelegate() {
    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }
                int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate) Closure cl) {
                    cl.delegate = new Delegate()
                    cl.resolveStrategy = Closure.DELEGATE_FIRST
                    cl() as int
                }
                int test() {
                    doIt {
                        @ASTTest(phase=INSTRUCTION_SELECTION, value={
                            node = node.rightExpression
                            def target = node.getNodeMetaData(DIRECT_METHOD_CALL_TARGET)
                            assert target != null
                            assert target.declaringClass.name == 'Delegate'
                        })
                        def x = f<caret>oo() // as the delegation strategy is delegate first, should return 2
                        x
                    }
                }
            }
            def o = new Owner()
            assert o.test() == 2
        ''', 'Delegate'
  }

  void testShouldAcceptMethodCall() {
    assertScript '''
            class ExecSpec {
                boolean called = false
                void foo() {
                    called = true
                }
            }

            ExecSpec spec = new ExecSpec()

            void exec(ExecSpec spec, @DelegatesTo(value=ExecSpec, strategy=Closure.DELEGATE_FIRST) Closure param) {
                param.delegate = spec
                param()
            }

            exec(spec) {
                fo<caret>o() // should be recognized because param is annotated with @DelegatesTo(ExecSpec)
            }
            assert spec.isCalled()
        ''', 'ExecSpec'
  }

  void testCallMethodFromOwner() {
    assertScript '''
            class Xml {
                boolean called = false
                void bar() { called = true }
                void foo(@DelegatesTo(Xml)Closure cl) { cl.delegate=this;cl() }
            }
            def mylist = [1]
            def xml = new Xml()
            xml.foo {
             mylist.each { b<caret>ar() }
            }
            assert xml.called
        ''', 'Xml'
  }



  void testInConstructor() {
    assertScript '''
        class Foo {
          def foo() {}
        }

        class Abc {
          def Abc(@DelegatesTo(Foo) Closure cl) {
          }
        }

        new Abc({fo<caret>o()})
''', 'Foo'
  }

  void assertScript(String text, String resolvedClass) {
    myFixture.configureByText('_a.groovy', text)

    final ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    final resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod)
    final containingClass = resolved.containingClass.name
    assertEquals(resolvedClass, containingClass)
  }
}
