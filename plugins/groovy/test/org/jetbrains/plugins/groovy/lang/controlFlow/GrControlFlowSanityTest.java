// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.controlFlow;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

/**
 * Contains samples of incorrect code, which although must not cause any problems to the CFG builder
 */
@TestApplication
public class GrControlFlowSanityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @Test
  public void testCFG1() {
    inference.get().doTest("""
             A qualityToolConfigurator = new Aa()
             qualityToolCo() {
               case "phpcs":
               qualityT<caret>oolConfigurator = 1
               break
             }
             """, "A");
  }
}
