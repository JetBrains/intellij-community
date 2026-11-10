// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GroovyDslTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/dsl");
  }

  private void doCustomTest(String s) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addGdsl(s);
    myFixture.testCompletion(myFixture.getTestName(false) + ".groovy", myFixture.getTestName(false) + "_after.groovy");
  }

  private void addGdsl(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addFileToProject(myFixture.getTestName(false) + "Enhancer.gdsl", text);
    GroovyDslFileIndex.activate(file.getVirtualFile());
  }

  public void doTest() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testCompletion(myFixture.getTestName(false) + ".gdsl", myFixture.getTestName(false) + "_after.gdsl");
  }

  @Test
  public void testCompleteTopLevel() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.gdsl", "<caret>");
    myFixture.completeBasic();
    List<String> expected = new ArrayList<String>(Arrays.asList("contributor", "contribute", "currentType", "assertVersion"));
    if (!myFixture.getLookupElementStrings().containsAll(expected)) {
      assertThat(expected).hasSameElementsAs(myFixture.getLookupElementStrings());
    }
  }

  @Test
  public void testCompleteInContributor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.gdsl", "contribute { <caret> }");
    myFixture.completeBasic();
    List<String> expected = new ArrayList<String>(Arrays.asList("method", "property", "parameter"));
    if (!myFixture.getLookupElementStrings().containsAll(expected)) {
      assertThat(expected).hasSameElementsAs(myFixture.getLookupElementStrings());
    }
  }

  @Test
  public void testCompleteClassMethod() {
    doCustomTest("""
                         def ctx = context(ctype: "java.lang.String")
                   
                         contributor ([ctx], {
                           method name:"zzz", type:"void", params:[:]
                         })
                   """);
  }

  @Test
  public void testOnAnonymousClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testDelegateToThrowable() {
    doCustomTest("""
                         def ctx = context(ctype: "java.lang.String")
                   
                         contributor ctx, {
                           findClass("java.lang.Throwable")?.methods?.each{add it}
                         }
                   """);
  }

  @Test
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

  @Test
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

  @Test
  public void testClassContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addGdsl("""
                   def ctx = context(scope: classScope(name: /.*WsSecurityConfig/))
                   contributor ctx, {
                     property name: "auxWsProperty", type: "java.lang.String"
                   }
              """);
    myFixture.testCompletionTyping(myFixture.getTestName(false) + ".groovy", "\n", myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testCategoryWhenMethodRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass category = myFixture.addClass("""
                                             public class MyCategory {
                                               public static void foo(String s) {}
                                             }
                                             """);
    PsiMethod foo = TestUtils.getMethods(category)[0];
    addGdsl("""
            
                  contributor([:]){category 'MyCategory'}\
              """);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElement(foo, "barrr", false, false));

    myFixture.testCompletion(myFixture.getTestName(false) + ".groovy", myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testPathRegexp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addGdsl("contributor(pathRegexp: '.*aaa.*') { property name:'fffooo', type:'int' }");

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("aaa/foo.groovy", "fff<caret>x").getVirtualFile());
    myFixture.completeBasic();

    Assertions.assertTrue(myFixture.getLookupElementStrings().contains("fffooo"));

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("bbb/foo.groovy", "fff<caret>x").getVirtualFile());
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).isNullOrEmpty();
  }

  @Test
  public void testNamedParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testNamedParametersGroovyConvention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> {
      PsiElement element = DocumentationManager.getInstance(myFixture.getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile());
      return new GroovyDocumentationProvider().generateDoc(element, null);
    });
  }

  @Test
  public void testCheckNamedArgumentTypes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testMethodDoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testPropertyDoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addGdsl("""
              contribute(currentType(String.name)) {
                property name:'foo', type:int, doc:'Some doc2'
              }""");
    myFixture.configureByText("a.groovy", "\"\".fo<caret>o");
    myFixture.completeBasic();
    Assertions.assertTrue(generateDoc().contains("Some doc2"));
    Assertions.assertTrue(generateDoc().contains("getFoo"));
  }

  @Test
  public void testVariableInAnnotationClosureContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    ReadAction.runBlocking(() -> assertNotNull(myFixture.getReferenceAtCaretPosition().resolve()));
  }

  @Test
  public void testVariableInMethodCallClosureContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addGdsl("""
                    contributor(scope: closureScope(methodName:'ensures')) {
                      variable(name: 'result', type:'java.lang.Object')
                    }
              """);

    myFixture.configureByText("a.groovy", """
            ensures{re<caret>sult == 2}
      """);

    ReadAction.runBlocking(() -> assertNotNull(myFixture.getReferenceAtCaretPosition().resolve()));
  }

  @Test
  public void testScriptSuperClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    ReadAction.runBlocking(() -> assertNotNull(myFixture.getReferenceAtCaretPosition().resolve()));
  }

  @Test
  public void testEnumConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      enum Myenum {
          a<caret>b(1, 2, 4)
      }
      """);

    addGdsl("""
            
              contribute(currentType("Myenum")) {
                  constructor params:[foo:Integer, bar:Integer, goo:Integer]
              }""");
    ReadAction.runBlocking(() -> assertNotNull(myFixture.getReferenceAtCaretPosition().resolve()));
  }

  @Test
  public void testCompleteBaseScriptMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
