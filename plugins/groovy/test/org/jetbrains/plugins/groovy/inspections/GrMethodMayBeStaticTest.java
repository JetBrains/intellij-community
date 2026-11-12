// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrMethodMayBeStaticTest extends GrMethodMayBeStaticTestBase {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);

  public GrMethodMayBeStaticTest() {
    super(projectFixture);
  }

  @Test
  public void testOldMain() {
    doTest("""
             class A {
                 void <warning descr="Method may be static">main</warning>() {
                   println "Hello world!"
                 }
             }
             """);
  }
}
