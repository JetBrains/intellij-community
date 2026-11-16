// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [StatefulEpInspection]. The project has the stubs of the PSI, project and extension API.
 */
@JvmOverloads
fun statefulEpFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("statefulEpFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass("package com.intellij.openapi.project; public interface Project {}")
  fixture.addClass("package com.intellij.psi; public interface PsiElement {}")
  fixture.addClass("package com.intellij.psi; public interface PsiReference {}")
  fixture.addClass("package com.intellij.codeInspection; public interface LocalQuickFix {}")
  fixture.addClass("package com.intellij.codeInspection; public interface ProblemDescriptor {}")
  fixture.addClass("package com.intellij.openapi.components; public interface ProjectComponent {}")
  fixture.addClass("package com.intellij.openapi.util; public class Ref<T> {}")
  fixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute {}")
  fixture.addClass("""
    import com.intellij.util.xmlb.annotations.Attribute;
    public class SD {
      @Attribute public String serviceInterface;
      @Attribute public String serviceImplementation;
      @Attribute public String instance;
      @Attribute public String service;
      @Attribute public String targetClass;
      @Attribute public String implementationClass;
    }
    """.trimIndent())
  fixture.enableInspections(StatefulEpInspection())
  initialized(pluginModule) {}
}
