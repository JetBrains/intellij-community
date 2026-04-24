// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet

internal class GradleTestIdeaProject private constructor() {

  var numHolderModules: Int = 1
  var projectName: String = "project"
  var projectSdkName: String? = null
  var moduleSdkName: String? = null

  private class TestIdeaProject(
      private val name: String,
      private val jdkName: String?,
      private val modules: List<IdeaModule>,
  ) : IdeaProject by notImplemented<IdeaProject>() {
    override fun getName(): String = name
    override fun getModules(): DomainObjectSet<out IdeaModule> = ImmutableDomainObjectSet.of(modules)
    override fun getJdkName(): String? = jdkName
    override fun getJavaLanguageSettings(): IdeaJavaLanguageSettings? = null
  }

  private class TestIdeaModule(
    private val name: String,
    private val jdkName: String?,
  ) : IdeaModule by notImplemented<IdeaModule>() {
    override fun getName(): String = name
    override fun getJdkName(): String? = jdkName
    override fun getJavaLanguageSettings(): IdeaJavaLanguageSettings? = null
  }

  companion object {

    fun testIdeaProject(configure: (GradleTestIdeaProject) -> Unit): IdeaProject {
      val configuration = GradleTestIdeaProject()
      configure(configuration)
      val modules = buildList {
        repeat(configuration.numHolderModules) { holderModuleIndex ->
          val holderModuleName = "module-$holderModuleIndex"
          add(TestIdeaModule(holderModuleName, configuration.moduleSdkName))
        }
      }
      return TestIdeaProject(configuration.projectName, configuration.projectSdkName, modules)
    }
  }
}