// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Assertions;

import java.util.LinkedHashMap;

public class SpockLineMarkersTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("spock/lang/Specification.groovy", """
      package spock.lang
      class Specification {}
      """);
  }

  public void testIsTestMethod() {
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
    GrTypeDefinition spec = file.getTypeDefinitions()[0];
    for (GrMethod method : spec.getCodeMethods()) {
      String name = method.getName();
      Assertions.assertEquals(map.get(name), TestFrameworks.getInstance().isTestMethod(method));
    }
  }

  public void testIsTestMethodInDumbMode() {
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
    GrTypeDefinition spec = file.getTypeDefinitions()[0];
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      for (GrMethod method : spec.getCodeMethods()) {
        String name = method.getName();
        Assertions.assertEquals(map.get(name), TestFrameworks.getInstance().isTestMethod(method));
      }
    });
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
