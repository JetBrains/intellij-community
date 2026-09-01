// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_CLASS_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_PLUGIN_ARTIFACT_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_PLUGIN_GROUP_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_RUNTIME_ARTIFACT_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_RUNTIME_GROUP_ID
import com.intellij.compose.ide.plugin.shared.REMEMBER_IN_COMPOSITION_CLASS_ID
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.GradleExtension
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import org.jetbrains.plugins.gradle.util.GradleConstants
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name

internal val Module.isComposeCompilerPluginApplied: Boolean
  get() {
    if (hasComposeGradlePluginExtension()) return true

    val settings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(this) ?: return false
    val classpaths = settings.compilerArguments?.pluginClasspaths ?: return false
    return classpaths.any(::isComposeCompilerPluginPath)
  }

private fun Module.hasComposeGradlePluginExtension(): Boolean {
  val mainModuleDataNode = CachedModuleDataFinder.findMainModuleData(this) ?: return false
  val extensions = getGradleExtensions(mainModuleDataNode) ?: return false
  return extensions.any { it.name == COMPOSE_PLUGIN_ID && it.typeFqn == COMPOSE_KOTLIN_PLUGIN_NAME }
}

private fun isComposeCompilerPluginPath(pathString: String): Boolean {
  val fileName = Path(pathString).name.lowercase()
  return fileName.contains("compose-compiler-plugin")
}

private fun getGradleExtensions(moduleDataNode: DataNode<*>): List<GradleExtension>? =
  ExternalSystemApiUtil.find(moduleDataNode, GradleExtensionsDataService.KEY)?.data?.extensions

/**
 * Resolves the Compose runtime library version from the Compose Gradle plugin
 * version found in the buildscript classpath.
 */
internal class K2ComposeRuntimeLibraryVersionProvider : KotlinLibraryVersionProvider {
  override fun getVersion(module: Module, groupId: String, artifactId: String): String? {
    if (groupId != COMPOSE_RUNTIME_GROUP_ID || artifactId != COMPOSE_RUNTIME_ARTIFACT_ID) return null

    val moduleNode = CachedModuleDataFinder.findMainModuleData(module) ?: return null

    val classpathData = ExternalSystemApiUtil.find(moduleNode, BuildScriptClasspathData.KEY)?.data ?: return null

    // The build script classpath of a subproject is prefixed with the entries inherited from its parent project
    // (see GradleBuildScriptClasspathModelBuilder.collectClasspathEntries), so iterate backwards to let the version
    // declared closest to this module win.
    return classpathData.classpathEntries.asReversed()
      .asSequence()
      .flatMap { it.classesFile.asSequence() }
      .firstNotNullOfOrNull(::extractComposePluginVersion)
  }

  /**
   * Extracts `<version>` from a `compose-gradle-plugin` jar path, supporting both the Gradle cache layout
   * (`…/org.jetbrains.compose/compose-gradle-plugin/<version>/…`) and the Maven repository layout
   * (`…/org/jetbrains/compose/compose-gradle-plugin/<version>/…`).
   */
  private fun extractComposePluginVersion(path: String): String? {
    val normalizedPath = PathUtil.toSystemIndependentName(path)

    return sequenceOf(
      "${COMPOSE_PLUGIN_GROUP_ID}/${COMPOSE_PLUGIN_ARTIFACT_ID}/",
      "${COMPOSE_PLUGIN_GROUP_ID.replace('.', '/')}/${COMPOSE_PLUGIN_ARTIFACT_ID}/",
    ).firstNotNullOfOrNull { marker ->
      normalizedPath.substringAfter(marker, "")
        .substringBefore('/')
        .takeIf(String::isNotBlank)
    }
  }
}

internal val Module.buildScriptKtFile: KtFile?
  get() {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) return null
    val projectPath = ExternalSystemApiUtil.getExternalProjectPath(this) ?: return null

    val path = Path(projectPath, GradleConstants.KOTLIN_DSL_SCRIPT_NAME)
    if (!path.exists()) return null

    val vFile = VfsUtil.findFile(path, true) ?: return null
    return PsiManager.getInstance(project).findFile(vFile) as? KtFile
  }

internal fun refreshGradleProject(module: Module) {
  val project = module.project
  val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return

  ExternalProjectsManager.getInstance(project).runWhenInitialized {
    val spec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
      .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      .build()

    ExternalSystemUtil.refreshProject(externalProjectPath, spec)
  }
}

@OptIn(KaExperimentalApi::class)
internal fun checkRequiresComposePlugin(expression: KtCallExpression): Boolean = analyze(expression) {
  val call = expression.tryResolveCall()?.single?.simple ?: return@analyze false

  isComposableInvocation(call) || isRememberInCompositionCall(call)
}

internal fun checkRequiresComposePlugin(expression: KtSimpleNameExpression): Boolean = analyze(expression) {
  val variableAccess = expression.resolveToCall()?.singleVariableAccessCall() ?: return@analyze false
  val propertySymbol = variableAccess.symbol as? KaPropertySymbol ?: return@analyze false
  val getter = propertySymbol.getter ?: return@analyze false
  COMPOSABLE_ANNOTATION_CLASS_ID in getter.annotations
}

@OptIn(KaExperimentalApi::class)
internal fun isComposableInvocation(memberCall: KaSimpleOrMultiCall): Boolean {
  if (memberCall !is KaSimpleCall<*, *>) return false

  fun hasComposableAnnotation(annotated: KaAnnotated?): Boolean {
    return annotated != null && COMPOSABLE_ANNOTATION_CLASS_ID in annotated.annotations
  }

  fun KaNamedFunctionSymbol.isInvokeOperatorCall(): Boolean {
    return isOperator && name == OperatorNameConventions.INVOKE
  }

  return when (val callableSymbol = memberCall.symbol) {
    is KaNamedFunctionSymbol -> {
      if (hasComposableAnnotation(callableSymbol)) return true

      if (!callableSymbol.isInvokeOperatorCall()) return false

      val typeInvokeOperatorIsCalledOn = memberCall.dispatchReceiver?.type ?: return false
      hasComposableAnnotation(typeInvokeOperatorIsCalledOn)
    }
    is KaPropertySymbol -> hasComposableAnnotation(callableSymbol.getter)
    else -> false
  }
}

@OptIn(KaExperimentalApi::class)
internal fun isRememberInCompositionCall(memberCall: KaSimpleCall<*, *>): Boolean {
  fun hasRememberInCompositionAnnotation(annotated: KaAnnotated?): Boolean {
    return annotated != null && REMEMBER_IN_COMPOSITION_CLASS_ID in annotated.annotations
  }

  return when (val callableSymbol = memberCall.symbol) {
    is KaNamedFunctionSymbol,
    is KaConstructorSymbol,
      -> hasRememberInCompositionAnnotation(callableSymbol)
    else -> false
  }
}

private const val COMPOSE_PLUGIN_ID = "composeCompiler"

private const val COMPOSE_KOTLIN_PLUGIN_NAME = "org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension"