// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JsonBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      def builder = new groovy.json.JsonBuilder()
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
  public void testBuilderCallsResolveAndReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (Map.Entry<String, String> entry : Map.of(
      "builder.root()", "java.util.Map",
      "builder.root {}", "java.util.Map",
      "builder.root(a:1)", "java.util.Map",
      "builder.root([a:1])", "java.util.Map",
      "builder.root([a:1]) {}", "java.util.Map",
      "builder.root(a:1) {}", "java.util.Map",
      "builder.root(a:1, {})", "java.util.Map",
      "builder.root({}, a:1)", "java.util.Map",
      "builder.root([1,\"\",new Object()], {})", "java.util.Map",
      "builder.root([1,\"\",new Object()] as Object[], {})", "java.util.Map"
    ).entrySet()) {
      String text = entry.getKey();
      String returnType = entry.getValue();
      var file = (GroovyFile)myFixture.configureByText("a.groovy", "def builder = new groovy.json.JsonBuilder(); " + text);
      ReadAction.runBlocking(() -> {
        GrTopStatement[] statements = file.getTopStatements();
        var call = (GrCallExpression)statements[statements.length - 1];
        assertNotNull(call.resolveMethod());
        assertEquals(returnType, call.getType().getCanonicalText());
      });
    }
  }

  @Test
  public void testBuilderInnerCallsResolveAndReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (Map.Entry<String, String> entry : Map.of(
      "noArg()", "java.util.List",
      "singleArg(1)", "java.lang.Integer",
      "singleArg(\"\")", "java.lang.String",
      "singleArg(new Object())", "java.lang.Object",
      "doubleArgs([1,2,3], {})", "java.util.List<java.lang.Integer>",
      "doubleArgs([] as Number[], {})", "java.util.List<java.lang.Number>",
      "varArgs(1,2,3)", "java.util.List<java.lang.Integer>",
      "varArgs(1,2l,3d)", "java.util.List<java.lang.Number>",
      "varArgs(1,2l,3d, new Object())", "java.util.List<java.lang.Object>"
    ).entrySet()) {
      String callText = entry.getKey();
      String returnType = entry.getValue();
      for (String text : List.of(
        "builder.root {<caret>" + callText + "}",
        "builder.root([a:1]) {<caret>" + callText + "}",
        "builder.root({<caret>" + callText + "}, a:1)"
      )) {
        myFixture.configureByText("a.groovy", "def builder = new groovy.json.JsonBuilder(); " + text);
        ReadAction.runBlocking(() -> {
          var reference = (GrReferenceExpression)myFixture.getReferenceAtCaretPosition();
          assertInstanceOf(PsiMethod.class, reference.resolve());
          assertEquals(returnType, reference.getType().getCanonicalText());
        });
      }
    }
  }

  @Test
  public void testBuilderDelegateInnerCallsResolveAndReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (Map.Entry<String, String> entry : Map.of(
      "noArg()", "java.util.List",
      "singleArg(1)", "java.lang.Integer",
      "singleArg(\"\")", "java.lang.String",
      "singleArg(new Object())", "java.lang.Object",
      "doubleArgs([1,2,3], {})", "java.util.List<java.lang.Integer>",
      "doubleArgs([] as Number[], {})", "java.util.List<java.lang.Number>",
      "varArgs(1,2,3)", "java.util.List<java.lang.Integer>",
      "varArgs(1,2l,3d)", "java.util.List<java.lang.Number>",
      "varArgs(1,2l,3d, new Object())", "java.util.List<java.lang.Object>"
    ).entrySet()) {
      String innerCallText = entry.getKey();
      String returnType = entry.getValue();
      for (String callText : List.of(
        "doubleArgs([1,2,3], {<caret>" + innerCallText + "})",
        "doubleArgs([] as Number[], {<caret>" + innerCallText + "})")) {
        for (String text : List.of(
          "builder.root {" + callText + "}",
          "builder.root([a:1]) {" + callText + "}",
          "builder.root({" + callText + "}, a:1)")) {
          myFixture.configureByText("a.groovy",
                                       "def builder = new groovy.json.JsonBuilder(); " + text);
          ReadAction.runBlocking(() -> {
            var reference =
              (GrReferenceExpression)myFixture.getReferenceAtCaretPosition();
            assertInstanceOf(PsiMethod.class, reference.resolve());
            assertEquals(returnType, reference.getType().getCanonicalText());
          });
        }
      }
    }
  }
}
