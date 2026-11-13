// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.transformations.impl.BindableTransformationSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.configureByExactName;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrBindableSupportTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK);

  public static Collection<Object[]> data() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("@Bindable on class", "@groovy.beans.Bindable class Person {}");
    map.put("@Bindable on class @CompileStatic", "@groovy.transform.CompileStatic @groovy.beans.Bindable class Person {}");
    map.put("@Bindable on field", "class Person { @groovy.beans.Bindable foo }");
    map.put("@Bindable on field @CompileStatic", "@groovy.transform.CompileStatic class Person { @groovy.beans.Bindable foo }");
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
    List<String> groovyTextList = Arrays.asList("new Person().addPropertyChangeListener {}",
                                                "new Person().addPropertyChangeListener('') {}",
                                                "new Person().removePropertyChangeListener {}",
                                                "new Person().removePropertyChangeListener('') {}",
                                                "new Person().firePropertyChange('', null, null)",
                                                "new Person().getPropertyChangeListeners()",
                                                "new Person().getPropertyChangeListeners('')");
    for (String text : groovyTextList) {
      GroovyFile file = (GroovyFile)configureByExactName(groovy.get(), "test.groovy", text);
      ReadAction.run(() -> {
        GrStatement[] statements = file.getStatements();
        GrCall call = (GrCall)statements[statements.length - 1];
        LightMethodBuilder method = (LightMethodBuilder)call.resolveMethod();
        assertNotNull(method);
        assertEquals(clazz, method.getContainingClass());
        assertEquals(BindableTransformationSupport.ORIGIN_INFO, method.getOriginInfo());
        assertNotNull(method.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN));
      });
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testJava(String name, String clazzText) {
    PsiClass clazz = addPerson(clazzText);
    List<String> javaTextList = Arrays.asList("new Person().addPropertyChangeListener(e -> {});",
                                              "new Person().addPropertyChangeListener(\"\", e -> {});",
                                              "new Person().removePropertyChangeListener(e -> {});",
                                              "new Person().removePropertyChangeListener(\"\", e -> {});",
                                              "new Person().firePropertyChange(\"\", null, null);",
                                              "new Person().getPropertyChangeListeners();",
                                              "new Person().getPropertyChangeListeners(\"\");");

    for (String text : javaTextList) {
      PsiJavaFile file = (PsiJavaFile)configureByExactName(groovy.get(), "Main.java", "class Main { void bar() {" + text + "} }");
      ReadAction.run(() -> {
        PsiExpressionStatement statement = (PsiExpressionStatement)file.getClasses()[0].getMethods()[0].getBody().getStatements()[0];
        PsiCall call = (PsiCall)statement.getExpression();
        LightMethodBuilder method = (LightMethodBuilder)call.resolveMethod();
        assertNotNull(method);
        assertEquals(method.getContainingClass(), clazz);
        assertEquals(BindableTransformationSupport.ORIGIN_INFO, method.getOriginInfo());
      });
    }
  }
}
