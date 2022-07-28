// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl

import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.gradle.codeInspection.GradlePluginDslStructureInspection
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins.*
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral


internal fun getStaticPluginModel(file: PsiFile): GradleStaticPluginModel {
  if (file !is GroovyFileBase || file.virtualFile?.extension?.endsWith(GradleConstants.EXTENSION) == false) {
    return EMPTY
  }
  return CachedValuesManager.getCachedValue(file) {
    val pluginsStatements = getPluginsBlock(file)
    if (pluginsStatements.isEmpty()) {
      CachedValueProvider.Result(EMPTY, PsiModificationTracker.MODIFICATION_COUNT)
    } else {
      CachedValueProvider.Result(computeStaticPluginModel(pluginsStatements), PsiModificationTracker.MODIFICATION_COUNT)
    }
  }
}

internal data class GradleStaticTask(val name: String, val description: String?, val configurationParameters: Map<String, String>)

internal data class GradleStaticExtension(val name: String, val type: String, val description: String?)

internal data class GradleStaticConfiguration(val name: String, val description: String?)

internal data class GradleStaticPluginModel(
  val tasks: Collection<GradleStaticTask>,
  val extensions: Collection<GradleStaticExtension>,
  val configurations: Collection<GradleStaticConfiguration>,
)

private val EMPTY = GradleStaticPluginModel(emptyList(), emptyList(), emptyList())

private val allDescriptors: List<GradleStaticPluginDescriptor> = GradleStaticPluginEntry.values().map { when(it) {
  GradleStaticPluginEntry.LIFECYCLE_BASE -> LifecycleBasePluginDescriptor()
  GradleStaticPluginEntry.BASE -> LifecycleBasePluginDescriptor()
  GradleStaticPluginEntry.JVM_ECOSYSTEM -> JvmEcosystemPluginDescriptor()
  GradleStaticPluginEntry.JVM_TOOLCHAINS -> JvmToolchainsPluginDescriptor()
  GradleStaticPluginEntry.REPORTING_BASE -> ReportingBasePluginDescriptor()
  GradleStaticPluginEntry.JAVA_BASE -> JavaBasePluginDescriptor()
  GradleStaticPluginEntry.JAVA -> JavaPluginDescriptor()
} }

private fun computeStaticPluginModel(statements: Array<GrStatement>) : GradleStaticPluginModel {
  val context = statements[0]
  val gradleVersion = GradleSettings.getInstance(context.project).getLinkedProjectSettings(context.getLinkedGradleProjectPath() ?: return EMPTY)?.resolveGradleVersion() ?: return EMPTY
  val extensions = allDescriptors.associateBy { it.pluginEntry.pluginName }
  val usedPluginDescriptorsList = statements.mapNotNull { extensions[extractPluginName(it)] }
  val pluginDescriptorMap = extensions.values.associateBy { it.pluginEntry }
  val sortedDescriptors = sortPlugins(usedPluginDescriptorsList, pluginDescriptorMap)
  val namespaceImpl = GradleStaticPluginNamespaceImpl()
  for (descriptor in sortedDescriptors) {
    with(descriptor) {
      namespaceImpl.configure(gradleVersion)
    }
  }
  return GradleStaticPluginModel(namespaceImpl.tasks, namespaceImpl.extensions, namespaceImpl.configurations)
}

private fun sortPlugins(usedPluginDescriptorsList: List<GradleStaticPluginDescriptor>,
                        map: Map<GradleStaticPluginEntry, GradleStaticPluginDescriptor>) : List<GradleStaticPluginDescriptor> {
  val sorted = mutableListOf<GradleStaticPluginDescriptor>()
  val processed = mutableSetOf<GradleStaticPluginEntry>()
  for (descriptor in usedPluginDescriptorsList) {
    if (descriptor.pluginEntry in processed) {
      continue
    }
    doSortPlugins(descriptor, map, sorted, processed)
  }
  return sorted
}

private fun doSortPlugins(root: GradleStaticPluginDescriptor, map: Map<GradleStaticPluginEntry, GradleStaticPluginDescriptor>, collector: MutableList<GradleStaticPluginDescriptor>, visited: MutableSet<GradleStaticPluginEntry>) {
  visited.add(root.pluginEntry)
  for (subClass in root.dependencies) {
    val nestedClass = map[subClass] ?: continue
    if (subClass in visited) {
      continue
    }
    doSortPlugins(nestedClass, map, collector, visited)
  }
  collector.add(root)
}

private fun getPluginsBlock(file: GroovyFileBase) : Array<GrStatement> {
  return file.statements.firstOrNull { it is GrMethodCall && it.invokedExpression.text == "plugins" }?.castSafelyTo<GrMethodCall>()?.let { GradlePluginDslStructureInspection.getStatements(it) } ?: emptyArray()
}

private fun extractPluginName(statement: GrStatement): String? {
  return statement.castSafelyTo<GrMethodCall>()?.takeIf { it.invokedExpression.text == "id" }?.expressionArguments?.firstOrNull()?.castSafelyTo<GrLiteral>()?.value?.castSafelyTo<String>()
}