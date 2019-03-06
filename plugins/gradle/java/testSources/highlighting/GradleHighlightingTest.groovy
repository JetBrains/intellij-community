// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.highlighting

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.junit.Test
import org.junit.runners.Parameterized

class GradleHighlightingTest extends GradleHighlightingBaseTest {

  /**
   * It's sufficient to run the test against one gradle version
   */
  @Parameterized.Parameters(name = "with Gradle-{0}")
  static Collection<Object[]> data() { [BASE_GRADLE_VERSION] }

  @Test
  void testConfiguration() throws Exception {
    importProject("")
    testHighlighting """
configurations.create("myConfiguration")
configurations.myConfiguration {
    transitive = false
}
"""
  }

  @Test
  void testConfigurationResolve() throws Exception {
    def result = testResolve """
configurations.create("myConfiguration")
configurations.myConfiguration {
    transitive = false
}
""", "transitive"
    assert result instanceof PsiMethod
    assert result.getName() == "setTransitive"
  }

  @Test
  void testGradleImplicitUsages() {
    // create dummy source file to have gradle buildSrc project imported
    createProjectSubFile("buildSrc/src/main/groovy/Dummy.groovy", "")
    importProject("")

    fixture.enableInspections(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspectionBase(true))
    testHighlighting "buildSrc/src/main/groovy/org/buildsrc/GrTask.groovy", """
package org.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class <warning>GrTask</warning> extends DefaultTask {
    @TaskAction
    private void action() {
    }
}
"""
  }
}
