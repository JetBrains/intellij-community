// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitUtil
import com.intellij.junit4.JUnitTestTreeNodeManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import org.jetbrains.plugins.groovy.spock.rt.SpockJUnitTestTreeNodeManager

class SpockRunConfigurationExtension : RunConfigurationExtension() {
  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is JUnitConfiguration
  }

  override fun <T : RunConfigurationBase<*>> updateJavaParameters(configuration: T,
                                                                  params: JavaParameters,
                                                                  runnerSettings: RunnerSettings?) {
    if (configuration !is JUnitConfiguration) {
      return
    }
    val module = configuration.configurationModule.module ?: return
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    val mainClass = configuration.persistentData.MAIN_CLASS_NAME ?: return
    DumbService.getInstance(module.project).runWithAlternativeResolveEnabled<Throwable> {
      val clazz = JavaPsiFacade.getInstance(module.project).findClass(mainClass, scope) ?: return@runWithAlternativeResolveEnabled
      if (!clazz.isSpockSpecification() || !(JUnitUtil.isJUnit4TestClass(clazz) || JUnitUtil.isJUnit3TestClass(clazz))) {
        // JUnit 5 relies on testId rather than method names, so custom testLocation is redundant
        return@runWithAlternativeResolveEnabled
      }
      params.classPath.add(PathUtil.getJarPathForClass(SpockJUnitTestTreeNodeManager::class.java))
      params.vmParametersList.addNotEmptyProperty(JUnitTestTreeNodeManager.JUNIT_TEST_TREE_NODE_MANAGER_ARGUMENT,
                                                  SpockJUnitTestTreeNodeManager::class.java.name)
    }
    return
  }
}