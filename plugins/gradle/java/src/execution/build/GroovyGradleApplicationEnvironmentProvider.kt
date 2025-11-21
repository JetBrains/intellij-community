// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.util.ScriptFileUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration

class GroovyGradleApplicationEnvironmentProvider : GradleBaseApplicationEnvironmentProvider() {
  override fun getMainClass(profile: JavaRunConfigurationBase): String {
    return "org.codehaus.groovy.tools.GroovyStarter"
  }

  override fun getConfigurationRunName(profile: JavaRunConfigurationBase): String? {
    return (profile as? GroovyScriptRunConfiguration)?.name
  }

  override fun configureParameters(runProfile: JavaRunConfigurationBase): JavaParameters? {
    if (runProfile !is GroovyScriptRunConfiguration) return null
    val scriptFile = ScriptFileUtil.findScriptFileByPath(runProfile.scriptPath) ?: return null
    val runner = runProfile.getScriptRunner() ?: return null

    return runProfile.createJavaParameters(scriptFile,runner)
  }

  override fun isApplicable(task: ExecuteRunConfigurationTask?): Boolean {
    val runProfile = task?.runProfile ?: return false
    if (runProfile !is JavaRunConfigurationBase || !isGroovyPluginEnabledAndLoaded(runProfile)) return false
    if(runProfile !is GroovyScriptRunConfiguration) return false
    return GroovyConfigUtils.getInstance().isVersionAtLeast(runProfile.module, GroovyConfigUtils.GROOVY5_0, false)
  }

  private fun isGroovyPluginEnabledAndLoaded(runProfile: JavaRunConfigurationBase): Boolean {
    val project = runProfile.project
    return CachedValuesManager.getManager(project).getCachedValue(runProfile) {
      val groovyPluginId = PluginId.getId("org.intellij.groovy")
      val pluginSet = PluginManagerCore.getPluginSet()
      CachedValueProvider.Result.create(pluginSet.isPluginEnabled(groovyPluginId), PsiModificationTracker.NEVER_CHANGED)
    }
  }
}