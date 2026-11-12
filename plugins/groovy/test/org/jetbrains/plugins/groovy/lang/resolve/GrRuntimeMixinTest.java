// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrRuntimeMixinTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testIsNotUnnecessarilyQualified() {
    highlighting.get().doTestHighlighting("""
                         class SomeCategoryClass {
                             static Integer addAmount(Integer self, int amount) { self + amount }
                         }
                         Integer.mixin(SomeCategoryClass)
                         assert 1.addAmount(1) == 2
                         """, GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class,
                       UnnecessaryQualifiedReferenceInspection.class);
  }
}
