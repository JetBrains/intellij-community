// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroovyDslTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/dsl";
  }

  private void doCustomTest(String s) {
    addGdsl(s);
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  private void addGdsl(String text) {
    final PsiFile file = myFixture.addFileToProject(getTestName(false) + "Enhancer.gdsl", text);
    GroovyDslFileIndex.activate(file.getVirtualFile());
  }

  public void doTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".gdsl", getTestName(false) + "_after.gdsl");
  }

  public void testCompleteTopLevel() {
    myFixture.configureByText("a.gdsl", "<caret>");
    myFixture.completeBasic();
    List<String> expected = new ArrayList<String>(Arrays.asList("contributor", "contribute", "currentType", "assertVersion"));
    if (!myFixture.getLookupElementStrings().containsAll(expected)) {
      UsefulTestCase.assertSameElements(expected, myFixture.getLookupElementStrings());
    }
  }

  public void testCompleteInContributor() {
    myFixture.configureByText("a.gdsl", "contribute { <caret> }");
    myFixture.completeBasic();
    List<String> expected = new ArrayList<String>(Arrays.asList("method", "property", "parameter"));
    if (!myFixture.getLookupElementStrings().containsAll(expected)) {
      UsefulTestCase.assertSameElements(expected, myFixture.getLookupElementStrings());
    }
  }

  public void testCompleteClassMethod() {
    doCustomTest("""
                         def ctx = context(ctype: "java.lang.String")
                   
                         contributor ([ctx], {
                           method name:"zzz", type:"void", params:[:]
                         })
                   """);
  }

  public void testOnAnonymousClass() {
    addGdsl("""
              import com.intellij.patterns.PsiJavaPatterns
              
              contribute(ctype:PsiJavaPatterns.psiClass()) {
                method name:'foo' + psiClass.name, type:void, params:[:]
              }""");
    myFixture.configureByText("a.groovy", """
      
      class Foo<T> {
        def foo(T t) {
          t.f<caret>
        }
      }
      """);
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "fooT", "finalize");
  }

  public void testDelegateToThrowable() {
    doCustomTest("""
                         def ctx = context(ctype: "java.lang.String")
                   
                         contributor ctx, {
                           findClass("java.lang.Throwable")?.methods?.each{add it}
                         }
                   """);
  }

  public void testDelegateToArgument() {
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
                   """);
  }

  public void testDelegateToArgument2() {
    doCustomTest("""
                         def ctx = context(scope: closureScope(isArgument: true))
                         contributor(ctx, {
                           def call = enclosingCall("boo")
                           if (call) {
                             delegatesTo(call.arguments[0]?.classType)
                           }
                         })
                   """);
  }

  public void testClassContext() {
    addGdsl("""
                   def ctx = context(scope: classScope(name: /.*WsSecurityConfig/))
                   contributor ctx, {
                     property name: "auxWsProperty", type: "java.lang.String"
                   }
              """);
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", "\n", getTestName(false) + "_after.groovy");
  }

  public void testCategoryWhenMethodRenamed() {
    PsiClass category = myFixture.addClass("""
                                             public class MyCategory {
                                               public static void foo(String s) {}
                                             }
                                             """);
    PsiMethod foo = TestUtils.getMethods(category)[0];
    addGdsl("""
              
                  contributor([:]){category 'MyCategory'}\
              """);
    myFixture.renameElement(foo, "barrr", false, false);

    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testPathRegexp() {
    addGdsl("contributor(pathRegexp: '.*aaa.*') { property name:'fffooo', type:'int' }");

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("aaa/foo.groovy", "fff<caret>x").getVirtualFile());
    myFixture.completeBasic();

    Assertions.assertTrue(myFixture.getLookupElementStrings().contains("fffooo"));

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("bbb/foo.groovy", "fff<caret>x").getVirtualFile());
    myFixture.completeBasic();
    UsefulTestCase.assertEmpty(myFixture.getLookupElementStrings());
  }

  public void testNamedParameters() {
    addGdsl("""
              contribute(currentType(String.name)) {
                method name:'foo', type:void, params:[:], namedParams:[
                  parameter(name:'param1', type:String),
                  parameter(name:'param2', type:Integer),
                ]
              }""");
    myFixture.configureByText("a.groovy", "\"\".foo(par<caret>)");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param1", "param2");
  }

  public void testNamedParametersGroovyConvention() {
    addGdsl("""
              contribute(currentType(String.name)) {
                method name:'foo', type:void, params:[args:[
                    parameter(name:'param1', type:String, doc:'My doc'),
                    parameter(name:'param2', type:Integer),
                  ]]
              }""");
    myFixture.configureByText("a.groovy", "\"\".foo(par<caret>)");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param1", "param2");
    Assertions.assertEquals(
      "<pre><span style=\"color:#000000;\">param1</span><span style=\"\">:</span> <span style=\"color:#000000;\">java.lang.String</span></pre><p>My doc</p>",
      generateDoc());
  }

  private String generateDoc() {
    PsiElement element = DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile());
    return new GroovyDocumentationProvider().generateDoc(element, null);
  }

  public void testCheckNamedArgumentTypes() {
    addGdsl("""
              contribute(currentType(String.name)) {
                method name:'foo', type:void, params:[args:[
                    parameter(name:'param1', type:File),
                    parameter(name:'param2', type:Integer),
                  ]]
              }""");
    myFixture.configureByText("a.groovy", """
      
      "".foo(param1:<warning descr="Type of argument 'param1' can not be 'Integer'">2</warning>, param2:2)
      """);
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    myFixture.checkHighlighting(true, false, false);
  }

  public void testMethodDoc() {
    addGdsl("""
              contribute(currentType(String.name)) {
                method name:'foo', type:void, params:[:], doc:'Some doc'
              }""");
    myFixture.configureByText("a.groovy", "\"\".fo<caret>o");
    myFixture.completeBasic();
    Assertions.assertTrue(generateDoc().contains("Some doc"));
    Assertions.assertTrue(generateDoc().contains("foo"));
    Assertions.assertTrue(generateDoc().contains("<span style=\"\">(</span><span style=\"\">)</span>"));
  }

  public void testPropertyDoc() {
    addGdsl("""
              contribute(currentType(String.name)) {
                property name:'foo', type:int, doc:'Some doc2'
              }""");
    myFixture.configureByText("a.groovy", "\"\".fo<caret>o");
    myFixture.completeBasic();
    Assertions.assertTrue(generateDoc().contains("Some doc2"));
    Assertions.assertTrue(generateDoc().contains("getFoo"));
  }

  public void testVariableInAnnotationClosureContext() {
    addGdsl("""
                    contributor(scope: closureScope(annotationName:'Ensures')) {
                      variable(name: 'result', type:'java.lang.Object')
                    }
              """);

    myFixture.configureByText("a.groovy", """
            @interface Ensures {}
      
            @Ensures({re<caret>sult == 2})
            def foo() {}
      """);

    TestCase.assertNotNull(myFixture.getReferenceAtCaretPosition().resolve());
  }

  public void testVariableInMethodCallClosureContext() {
    addGdsl("""
                    contributor(scope: closureScope(methodName:'ensures')) {
                      variable(name: 'result', type:'java.lang.Object')
                    }
              """);

    myFixture.configureByText("a.groovy", """
            ensures{re<caret>sult == 2}
      """);

    TestCase.assertNotNull(myFixture.getReferenceAtCaretPosition().resolve());
  }

  public void testScriptSuperClass() {
    myFixture.addClass("""
                               public class Abc {
                                 public void foo() {}
                               }
                         """);
    addGdsl("""
                    scriptSuperClass(pattern: 'a.groovy', superClass: 'Abc')
              """);

    myFixture.configureByText("a.groovy", """
            fo<caret>o()
      """);

    TestCase.assertNotNull(myFixture.getReferenceAtCaretPosition().resolve());
  }

  public void testEnumConstructor() {
    myFixture.configureByText("a.groovy", """
      enum Myenum {
          a<caret>b(1, 2, 4)
      }
      """);

    addGdsl("""
              
              contribute(currentType("Myenum")) {
                  constructor params:[foo:Integer, bar:Integer, goo:Integer]
              }""");
    TestCase.assertNotNull(myFixture.getReferenceAtCaretPosition().resolve());
  }

  public void testCompleteBaseScriptMembers() {
    addGdsl("""
                        scriptSuperClass(pattern: 'a.groovy', superClass: 'pckg.MyBaseScriptClass')
                        """);
    myFixture.addFileToProject("pckg/MyBaseScriptClass.groovy", """
      package pckg
      
      abstract class MyBaseScriptClass extends Script {
          def foo() {}
          def prop
      }
      """);
    myFixture.configureByText("a.groovy", "<caret>");
    myFixture.completeBasic();

    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    Assertions.assertTrue(lookupElementStrings.contains("foo") && lookupElementStrings.contains("prop"));
  }
}
