// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils.GROOVY2_3
import org.jetbrains.plugins.groovy.config.getSdkVersion
import org.jetbrains.plugins.groovy.util.hasAcceptableModuleType
import org.jetbrains.plugins.groovy.util.hasJavaSdk

private fun Project.modules(): Array<out Module> = ModuleManager.getInstance(this).modules

internal fun isApplicableModule(module: Module): Boolean = hasJavaSdk(module) && hasAcceptableModuleType(module)

internal fun hasAnyApplicableModule(project: Project): Boolean = project.modules().any(::isApplicableModule)

internal fun getApplicableModules(project: Project): List<Module> = project.modules().filter(::isApplicableModule)

fun getWorkingDirectory(module: Module): String? {
  return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: module.project.basePath
}

internal fun hasNeededDependenciesToRunConsole(module: Module): Boolean {
  val sdkVersion = getSdkVersion(module)
  return sdkVersion != null && hasNeededDependenciesToRunConsole(module, sdkVersion)
}

internal fun sdkVersionIfHasNeededDependenciesToRunConsole(module: Module): @NlsSafe String? {
  val sdkVersion = getSdkVersion(module)
  return sdkVersion?.takeIf {
    hasNeededDependenciesToRunConsole(module, it)
  }
}

private fun hasNeededDependenciesToRunConsole(module: Module, sdkVersion: String): Boolean {
  val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
  val facade = JavaPsiFacade.getInstance(module.project)
  if (facade.findClass("groovy.ui.GroovyMain", scope) == null) {
    return false
  }
  if (StringUtil.compareVersionNumbers(sdkVersion, GROOVY2_3) >= 0) {
    return true
  }
  else {
    // groovy < 2.3 needs commons-cli:commons-cli jar
    // groovy-all has repackaged jarjar inside
    // regular groovy has optional dependency on regular commons-cli
    return facade.findClass("org.apache.commons.cli.CommandLineParser", scope) != null ||
           facade.findClass("groovyjarjarcommonscli.CommandLineParser", scope) != null
  }
}
