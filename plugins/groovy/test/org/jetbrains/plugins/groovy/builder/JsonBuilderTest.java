// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;

import java.util.List;
import java.util.Map;

public class JsonBuilderTest extends LightGroovyTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testHighlighting() {
    getFixture().configureByText("a.groovy", """
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
    getFixture().enableInspections(GroovyAssignabilityCheckInspection.class, GrUnresolvedAccessInspection.class);
    getFixture().checkHighlighting(true, false, true);
  }

  public void testBuilderCallsResolveAndReturnType() {
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
      var file = (GroovyFile)getFixture().configureByText("a.groovy", "def builder = new groovy.json.JsonBuilder(); " + text);
      GrTopStatement[] statements = file.getTopStatements();
      var call = (GrCallExpression)statements[statements.length - 1];
      assertNotNull(call.resolveMethod());
      assertEquals(returnType, call.getType().getCanonicalText());
    }
  }

  public void testBuilderInnerCallsResolveAndReturnType() {
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
        getFixture().configureByText("a.groovy", "def builder = new groovy.json.JsonBuilder(); " + text);
        var reference = (GrReferenceExpression)getFixture().getReferenceAtCaretPosition();
        assertInstanceOf(reference.resolve(), PsiMethod.class);
        assertEquals(returnType, reference.getType().getCanonicalText());
      }
    }
  }

  public void testBuilderDelegateInnerCallsResolveAndReturnType() {
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
          getFixture().configureByText("a.groovy",
                                       "def builder = new groovy.json.JsonBuilder(); " + text);
          var reference =
            (GrReferenceExpression)getFixture().getReferenceAtCaretPosition();
          assertInstanceOf(reference.resolve(), PsiMethod.class);
          assertEquals(returnType, reference.getType().getCanonicalText());
        }
      }
    }
  }
}
