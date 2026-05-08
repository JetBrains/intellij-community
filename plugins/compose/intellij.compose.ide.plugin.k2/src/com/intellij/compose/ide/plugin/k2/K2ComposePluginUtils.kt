// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_CLASS_ID
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
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.GradleExtension
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import org.jetbrains.plugins.gradle.util.GradleConstants
import kotlin.io.path.Path
import kotlin.io.path.exists

internal val Module.isComposeCompilerPluginApplied: Boolean
  get() {
    val mainModuleDataNode = CachedModuleDataFinder.findMainModuleData(this) ?: return false
    val extensions = getGradleExtensions(mainModuleDataNode) ?: return false
    return extensions.any { ext ->
      ext.name == COMPOSE_PLUGIN_ID &&
      ext.typeFqn == COMPOSE_KOTLIN_PLUGIN_NAME
    }
  }

private fun getGradleExtensions(moduleDataNode: DataNode<*>): List<GradleExtension>? =
  ExternalSystemApiUtil.find(moduleDataNode, GradleExtensionsDataService.KEY)?.data?.extensions

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

internal fun checkRequiresComposePlugin(expression: KtCallExpression): Boolean = analyze(expression) {
  val call = expression.resolveToCall()?.singleFunctionCallOrNull() as? KaCallableMemberCall<*, *>
             ?: return@analyze false

  isComposableInvocation(call) || isRememberInCompositionCall(call)
}

internal fun checkRequiresComposePlugin(expression: KtSimpleNameExpression): Boolean = analyze(expression) {
  val variableAccess = expression.resolveToCall()?.singleVariableAccessCall() ?: return@analyze false
  val propertySymbol = variableAccess.symbol as? KaPropertySymbol ?: return@analyze false
  val getter = propertySymbol.getter ?: return@analyze false
  COMPOSABLE_ANNOTATION_CLASS_ID in getter.annotations
}

@OptIn(KaExperimentalApi::class)
internal fun KaSession.isComposableInvocation(memberCall: KaCallableMemberCall<*, *>): Boolean {
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

      val typeInvokeOperatorIsCalledOn = memberCall.partiallyAppliedSymbol.dispatchReceiver?.type ?: return false
      hasComposableAnnotation(typeInvokeOperatorIsCalledOn)
    }
    is KaPropertySymbol -> hasComposableAnnotation(callableSymbol.getter)
    else -> false
  }
}

internal fun KaSession.isRememberInCompositionCall(memberCall: KaCallableMemberCall<*, *>): Boolean {
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