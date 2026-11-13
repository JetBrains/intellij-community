// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class UnnecessaryQualifiedReferenceInspectionTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void attributeExpression() {
    runInEdtAndWait(() -> {
      highlightingTest("""
                         class A { static foo }
                         A.@foo
                         A.@foo()
                         """);
    });
  }
  
  @Test
  public void testStaticInterfaceMembers() {
    runInEdtAndWait(() -> {
      highlightingTest("""
                         interface Capibration {
                             int PI = 3
                             static void x() {
                                 println <warning descr="Unnecessary qualified reference">Capibration</warning>.PI
                                 <warning descr="Unnecessary qualified reference">Capibration</warning>.y()
                             }
                       
                             static void y() {}
                         }
                         """);
    });
  }

  @Override
  public final @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(UnnecessaryQualifiedReferenceInspection.class);
  }

}
