// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class SpockLineMarkersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("spock/lang/Specification.groovy", """
      package spock.lang
      class Specification {}
      """);
  }

  @Test
  public void testIsTestMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = (GroovyFile)myFixture.configureByText("MySpec.groovy", """
      class MySpec extends spock.lang.Specification {
        def cleanup() { expect: 1 == 1 }
        def methodWithoutLabels() {}
        def methodWithAnotherLabel() { expect2: 1 == 1 }
        <caret>def 'method with spaces ok'() { expect: 1 == 1 }
      }
      """);
    ReadAction.runBlocking(() -> {
      LinkedHashMap<String, Boolean> map = new LinkedHashMap<>(4);
      map.put("cleanup", false);
      map.put("methodWithoutLabels", false);
      map.put("methodWithAnotherLabel", false);
      map.put("method with spaces ok", true);
      GrTypeDefinition spec = file.getTypeDefinitions()[0];
      for (GrMethod method : spec.getCodeMethods()) {
        String name = method.getName();
        Assertions.assertEquals(map.get(name), TestFrameworks.getInstance().isTestMethod(method));
      }
    });
  }

  @Test
  public void testIsTestMethodInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = (GroovyFile)myFixture.configureByText("MySpec.groovy", """
      class MySpec extends spock.lang.Specification {
        def cleanup() { expect: 1 == 1 }
        def methodWithoutLabels() {}
        def methodWithAnotherLabel() { expect2: 1 == 1 }
        <caret>def 'method with spaces ok'() { expect: 1 == 1 }
      }
      """);
    LinkedHashMap<String, Boolean> map = new LinkedHashMap<>(4);
    map.put("cleanup", false);
    map.put("methodWithoutLabels", false);
    map.put("methodWithAnotherLabel", false);
    map.put("method with spaces ok", true);
    GrTypeDefinition spec = ReadAction.computeBlocking(() -> file.getTypeDefinitions()[0]);
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> ReadAction.runBlocking(() -> {
      for (GrMethod method : spec.getCodeMethods()) {
        String name = method.getName();
        Assertions.assertEquals(map.get(name), TestFrameworks.getInstance().isTestMethod(method));
      }
    }));
  }
}
