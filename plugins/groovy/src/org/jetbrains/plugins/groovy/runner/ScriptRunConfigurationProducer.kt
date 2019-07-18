// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.runner

import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.util.ScriptFileUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.util.text.nullize
import org.jetbrains.plugins.groovy.console.GroovyConsoleStateService
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyRunnerPsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyRunnerPsiUtil.isRunnable
import org.jetbrains.plugins.groovy.runner.GroovyRunnerUtil.getConfigurationName

class ScriptRunConfigurationProducer : LazyRunConfigurationProducer<GroovyScriptRunConfiguration>() {

  override fun getConfigurationFactory(): ConfigurationFactory = GroovyScriptRunConfigurationType.getInstance().configurationFactory

  private class Data(
    val location: Location<PsiElement>,
    val element: PsiElement,
    val clazz: PsiClass,
    val file: GroovyFile,
    val virtualFile: VirtualFile,
    val scriptType: GroovyRunnableScriptType
  )

  private fun checkAndExtractData(context: ConfigurationContext): Data? {
    val location = context.location ?: return null
    val element = location.psiElement
    val file = element.containingFile as? GroovyFile ?: return null
    val virtualFile = file.virtualFile ?: return null
    val scriptType = GroovyScriptUtil.getScriptType(file)
    if (scriptType.runner == null) {
      return null
    }
    if (GroovyConsoleStateService.getInstance(location.project).isProjectConsole(virtualFile)) {
      return null
    }
    val clazz = GroovyRunnerPsiUtil.getRunningClass(element) ?: return null
    if (clazz !is GroovyScriptClass && !isRunnable(clazz)) {
      return null
    }
    return Data(location, element, clazz, file, virtualFile, scriptType)
  }

  override fun setupConfigurationFromContext(configuration: GroovyScriptRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElement: Ref<PsiElement>): Boolean {
    val data = checkAndExtractData(context) ?: return false
    sourceElement.set(data.element)
    configuration.setupFromClass(data.clazz, data.virtualFile)
    data.scriptType.tuneConfiguration(data.file, configuration, data.location)
    return true
  }

  private fun GroovyScriptRunConfiguration.setupFromClass(aClass: PsiClass, virtualFile: VirtualFile) {
    workingDirectory = virtualFile.parent?.path
    scriptPath = ScriptFileUtil.getScriptFilePath(virtualFile)
    name = getConfigurationName(aClass, configurationModule).nullize() ?: virtualFile.name
    module = JavaExecutionUtil.findModule(aClass)
  }

  override fun isConfigurationFromContext(configuration: GroovyScriptRunConfiguration, context: ConfigurationContext): Boolean {
    val data = checkAndExtractData(context) ?: return false
    return configuration.scriptPath == ScriptFileUtil.getScriptFilePath(data.virtualFile)
           && data.scriptType.isConfigurationByLocation(configuration, data.location)
  }
}
