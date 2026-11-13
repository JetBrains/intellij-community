// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.transformations.impl.VetoableTransformationSupportKt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrVetoableSupportTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  public static Collection<Object[]> data() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("@Vetoable on class", "@groovy.beans.Vetoable class Person {}");
    map.put("@Vetoable on class @CompileStatic", "@groovy.transform.CompileStatic @groovy.beans.Vetoable class Person {}");
    map.put("@Vetoable on field", "class Person { @groovy.beans.Vetoable foo }");
    map.put("@Vetoable on field @CompileStatic", "@groovy.transform.CompileStatic class Person { @groovy.beans.Vetoable foo }");
    return ContainerUtil.map(map.entrySet(), e -> new Object[]{e.getKey(), e.getValue()});
  }

  private PsiClass addPerson(String clazzText) {
    groovy.get().addFileToProject("Person.groovy", clazzText);
    PsiClass clazz = ReadAction.computeBlocking(() -> groovy.get().findClass("Person"));
    assertNotNull(clazz);
    return clazz;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testGroovy(String name, String clazzText) {
    PsiClass clazz = addPerson(clazzText);
    groovy.get().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);

    List<String> groovyTextList = Arrays.asList("new Person().addVetoableChangeListener {}",
                                                "new Person().addVetoableChangeListener('') {}",
                                                "new Person().removeVetoableChangeListener {}",
                                                "new Person().removeVetoableChangeListener('') {}",
                                                "new Person().fireVetoableChange('', null, null)",
                                                "new Person().getVetoableChangeListeners()",
                                                "new Person().getVetoableChangeListeners('')");

    for (String text : groovyTextList) {
      GroovyFile file = (GroovyFile)groovy.get().configureByText("test.groovy", text);
      ReadAction.run(() -> {
        GrStatement[] statements = file.getStatements();
        GrCall call = (GrCall)statements[statements.length - 1];
        GrLightMethodBuilder method = (GrLightMethodBuilder)call.resolveMethod();
        assertNotNull(method);
        assertEquals(clazz, method.getContainingClass());
        assertEquals(VetoableTransformationSupportKt.ORIGIN_INFO, method.getOriginInfo());
        assertNotNull(method.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN));
      });
      groovy.get().checkHighlighting();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testJava(String name, String clazzText) {
    PsiClass clazz = addPerson(clazzText);
    IdeaTestUtil.setProjectLanguageLevel(groovy.get().getProject(), LanguageLevel.HIGHEST, groovy.get().getTestRootDisposable());
    List<String> javaTextString = Arrays.asList("new Person().addVetoableChangeListener(e -> {});",
                                                "new Person().addVetoableChangeListener(\"\", e -> {});",
                                                "new Person().removeVetoableChangeListener(e -> {});",
                                                "new Person().removeVetoableChangeListener(\"\", e -> {});",
                                                "new Person().fireVetoableChange(\"\", null, null);",
                                                "new Person().getVetoableChangeListeners();",
                                                "new Person().getVetoableChangeListeners(\"\");");

    for (String text : javaTextString) {
      PsiJavaFile file = (PsiJavaFile)groovy.get().configureByText("Main.java", "class Main { void bar() {" + text + "} }");
      ReadAction.run(() -> {
        PsiExpressionStatement statement = (PsiExpressionStatement)file.getClasses()[0].getMethods()[0].getBody().getStatements()[0];
        PsiCall call = (PsiCall)statement.getExpression();
        GrLightMethodBuilder method = (GrLightMethodBuilder)call.resolveMethod();
        assertNotNull(method);
        assertEquals(method.getContainingClass(), clazz);
        assertEquals(VetoableTransformationSupportKt.ORIGIN_INFO, method.getOriginInfo());
      });
      groovy.get().checkHighlighting();
    }
  }
}
