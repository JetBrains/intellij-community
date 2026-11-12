// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class StreamingJsonBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy",
                              """
                                def builder = new groovy.json.StreamingJsonBuilder(null)
                                builder.people {
                                    person {
                                        string "fdsasdf"
                                        mapCall(
                                                city: 'A',
                                                country: 'B',
                                                zip: 12345,
                                        )
                                        boolCall true
                                        varArgs '1111', 22222
                                        empty()
                                        cc {
                                          foobar()
                                          hellYeah(1,2,3)
                                        }
                                        <warning>someProperty</warning>
                                    }
                                    <warning>someProperty</warning>
                                }

                                builder.<warning>root</warning>
                                builder.root<warning>(new Object())</warning>
                                builder.root<warning>(new Object[0])</warning>
                                builder.root<warning>([new Object(), new Object()])</warning>
                                builder.root<warning>([], new Object(), {})</warning>
                                """);
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class, GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testBuilderCallsResolveReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (String text : List.of("builder.root()", "builder.root {}", "builder.root(a: 1, b: 2)", "builder.root(a: 1, b: 2) {}",
                               "builder.root([1, 2, 3, 4]) {}", "builder.root([] as Integer[], {})")) {
      GroovyFile file =
        (GroovyFile)myFixture.configureByText("a.groovy", "def builder = new groovy.json.StreamingJsonBuilder(null);" + text);
      ReadAction.runBlocking(() -> {
        GrTopStatement[] statements = file.getTopStatements();
        GrCallExpression call = (GrCallExpression)statements[statements.length - 1];
        assertNotNull(call.resolveMethod());
        assertEquals("groovy.json.StreamingJsonBuilder", call.getType().getCanonicalText());
      });
    }
  }

  @Test
  public void testBuilderInnerCallsResolveReturnType() {
    for (String callText : List.of("noArg()", "singleArg(1)", "singleArg(new Object())", "singleArg {}", "singleArg([:])",
                                   "doubleArg([:]) {}",
                                   "doubleArg(1, 2)", "doubleArg([], {})", "doubleArg(new Object[0]) {}", "doubleArg(new Object[0], {})",
                                   "varArg(1, 2, 3)", "varArg(1, 2d, '')", "varArg(new Object(), [], {}, a: 1, 2d, [:], '')")) {
      for (String text : List.of("builder.root {<caret>" + callText + "}",
                                 "builder.root(a: 1, b: 2) {<caret>" + callText + "}",
                                 "builder.root([1, 2, 3, 4]) {<caret>" + callText + "}",
                                 "builder.root([] as Integer[], {<caret>" + callText + "})")) {
        doTest(text);
      }
    }
  }

  @Test
  public void test_builder_delegate_inner_calls_resolve___return_type() {
    for (String innerCallText : List.of("noArg()", "singleArg(1)", "singleArg(new Object())", "singleArg {}", "singleArg([:])",
                                        "doubleArg([:]) {}",
                                        "doubleArg(1, 2)", "doubleArg([], {})", "doubleArg(new Object[0]) {}",
                                        "doubleArg(new Object[0], {})",
                                        "varArg(1, 2, 3)", "varArg(1, 2d, '')", "varArg(new Object(), [], {}, a: 1, 2d, [:], '')")) {
      for (String callText : List.of("singleArg {<caret>" + innerCallText + "}",
                                     "doubleArg([:]) {<caret>" + innerCallText + "}",
                                     "doubleArg([], {<caret>" + innerCallText + "})",
                                     "doubleArg(new Object[0], {<caret>" + innerCallText + "})",
                                     "varArg(new Object(), [], {<caret>" + innerCallText + "}, a: 1, 2d, [:], '')")) {
        for (String s : List.of("builder.root {" + callText + "}",
                                "builder.root(a: 1, b: 2) {" + callText + "}",
                                "builder.root([1, 2, 3, 4]) {" + callText + "}",
                                "builder.root([] as Integer[], {" + callText + "})")) {
          doTest(s);
        }
      }
    }
  }

  @Test
  public void testOwnerFirst() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      def foo(String s) {}
      new groovy.json.StreamingJsonBuilder().root {
        fo<caret>o ""
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()).resolve();
      assertInstanceOf(GrMethod.class, resolved);
      assertFalse(resolved instanceof LightElement);
      assertTrue(resolved.isPhysical());
    });
  }

  private void doTest(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "def builder = new groovy.json.StreamingJsonBuilder(); " +
                                             DefaultGroovyMethods.invokeMethod(String.class, "valueOf", new Object[]{text}));
    ReadAction.runBlocking(() -> {
      GrReferenceExpression reference = (GrReferenceExpression)myFixture.getReferenceAtCaretPosition();
      assertInstanceOf(PsiMethod.class, reference.resolve());
      assertEquals("java.lang.Object", reference.getType().getCanonicalText());
    });
  }

  @Test
  public void testDoNotOverrideExistingMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = (GroovyFile) myFixture.configureByText("a.groovy", """
      new groovy.json.StreamingJsonBuilder().cal<caret>l {}
      """);
    ReadAction.runBlocking(() -> {
      GrTopStatement[] statements = file.getTopStatements();
      GrCallExpression call = (GrCallExpression) statements[statements.length - 1];
      Object method = call.resolveMethod();
      assertNotNull(method);
      assertInstanceOf(ClsMethodImpl.class, method);
    });
  }
}
