// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind

/**
 * The reason for a [ResolvedModuleKind].
 *
 * The model holds names only and no PSI, because the analysis cache holds it.
 * [toPlainText] renders the reason as the text of the inspections and the tooltips.
 */
internal sealed interface ModuleKindReason {
  /** The analysis found no descriptor to analyze. */
  data object None : ModuleKindReason

  /** The split-mode configuration predefines the kind of the module. */
  data class Predefined(val reasoning: @NlsSafe String) : ModuleKindReason

  /**
   * The own dependencies of the descriptor give the kind.
   *
   * @property evidence the dependency that gives the kind, or null when the analysis shows no dependency.
   * @property isRpcMissing true when a frontend descriptor has no dependency on `intellij.platform.rpc`.
   */
  data class OwnDependency(val evidence: KindEvidence?, val isRpcMissing: Boolean) : ModuleKindReason

  /** The descriptor, or its containing plugins, have both frontend and backend dependencies. */
  data class MixedDependencies(val frontendEvidence: KindEvidence?, val backendEvidence: KindEvidence?) : ModuleKindReason

  /** The content module has no own frontend or backend dependencies, and its containing plugins give the kind. */
  data class ContainingPlugins(val plugins: List<ContainingPluginKind>) : ModuleKindReason

  /**
   * The analysis found no frontend or backend dependency.
   *
   * @property dependencyNames the dependencies that the analysis found and that give no kind.
   */
  data class NoPlatformDependencies(val target: AnalysisTarget, val dependencyNames: List<String>) : ModuleKindReason
}

/**
 * A dependency of the [kind] side and the path to it.
 * The [kind] is [ModuleKind.FRONTEND], [ModuleKind.BACKEND], or [ModuleKind.MONOLITH].
 */
internal data class KindEvidence(
  val kind: ModuleKind,
  val dependency: DependencyInfo,
)

/**
 * @property pluginId the plugin id of the containing plugin descriptor, or its plugin name.
 */
internal data class ContainingPluginKind(
  val moduleName: String,
  val descriptorName: String,
  val pluginId: String?,
  val kind: ModuleKind,
)

internal data class AnalysisTarget(
  val moduleName: String,
  val descriptorName: String?,
)

/**
 * One step of a [DependencyTrace].
 * The steps go from the analyzed descriptor to the dependency.
 */
internal sealed interface DependencyTraceStep {
  /**
   * The path goes through this descriptor.
   *
   * @property moduleName the IntelliJ module that holds the descriptor file. The plain text gives it to locate the file.
   * @property pluginId the plugin id of the descriptor, or its plugin name. It is null for a content module descriptor.
   */
  data class Descriptor(val descriptorName: String, val moduleName: String?, val pluginId: String? = null) : DependencyTraceStep

  /** The previous descriptor depends on [dependencyName]. The next step is in the descriptor of this dependency. */
  data class ViaDependency(val dependencyName: String) : DependencyTraceStep

  /** The previous descriptor loads a content module with [loadingRule]. The next step is in this content module. */
  data class ContentModule(val loadingRule: ModuleLoadingRule) : DependencyTraceStep

  /** The next step is in a plugin descriptor that includes the analyzed content module. */
  data object ContainingPlugin : DependencyTraceStep

  /**
   * The split-mode configuration predefines the kind of the module [moduleName].
   * This module ends the path.
   *
   * @property descriptorName the descriptor of [moduleName] that the analysis resolved.
   * @property pluginId the plugin id of this descriptor, or its plugin name. It is null for a content module descriptor.
   */
  data class Predefined(
    val reasoning: @NlsSafe String,
    val moduleName: String,
    val descriptorName: String,
    val pluginId: String? = null,
  ) : DependencyTraceStep

  /** The predefined [kind] dependency [dependencyName] implies the dependency on `intellij.platform.rpc`. */
  data class ImpliedRpc(val kind: ModuleKind, val dependencyName: String) : DependencyTraceStep
}

internal fun ModuleKindReason.toPlainText(): @NlsSafe String {
  return when (this) {
    ModuleKindReason.None -> ""
    is ModuleKindReason.Predefined -> reasoning
    is ModuleKindReason.OwnDependency -> listOfNotNull(
      evidence?.toPlainText(),
      if (isRpcMissing) "No dependency on '$RPC_MODULE_NAME' was found" else null,
    ).joinToString("\n")
    is ModuleKindReason.MixedDependencies -> listOfNotNull(
      frontendEvidence?.toPlainText(),
      backendEvidence?.toPlainText(),
    ).joinToString("\n\n")
    is ModuleKindReason.ContainingPlugins -> {
      if (plugins.isEmpty()) {
        "No containing plugin descriptors were found"
      }
      else {
        "Module declares no own FE/BE dependencies, but the containing plugin.xml files do:\n" +
        plugins.joinToString("\n") { plugin -> "Module '${plugin.moduleName}'  -> ${plugin.kind.id}" }
      }
    }
    is ModuleKindReason.NoPlatformDependencies -> {
      if (dependencyNames.isEmpty()) {
        "No frontend or backend dependencies were found for ${target.toPlainText()}"
      }
      else {
        "No frontend or backend dependencies were found among:\n${dependencyNames.joinToString("\n") { "'$it'" }}"
      }
    }
  }
}

private fun KindEvidence.toPlainText(): String {
  return "${getEvidenceKindName(kind)} dependency '${dependency.name}' from ${dependency.trace.toPlainText()}"
}

private fun getEvidenceKindName(kind: ModuleKind): String {
  return when (kind) {
    ModuleKind.FRONTEND -> "Frontend"
    ModuleKind.BACKEND -> "Backend"
    ModuleKind.MONOLITH -> "Monolith"
    ModuleKind.LITE_FRONTEND,
    ModuleKind.MIXED,
    ModuleKind.SHARED,
    is ModuleKind.Composite,
      -> error("Unsupported module kind for reasoning: $kind")
  }
}

private fun AnalysisTarget.toPlainText(): String {
  return if (descriptorName != null) "descriptor '$descriptorName' in module '$moduleName'" else "module '$moduleName'"
}

/**
 * A path that has a [DependencyTraceStep.ViaDependency] step ends with a period.
 */
internal fun DependencyTrace.toPlainText(): @NlsSafe String {
  return buildString {
    for (step in steps) {
      when (step) {
        is DependencyTraceStep.Descriptor -> {
          append("descriptor '").append(step.descriptorName).append("'")
          if (step.moduleName != null) {
            append(" in module '").append(step.moduleName).append("'")
          }
        }
        is DependencyTraceStep.ViaDependency -> append("\nvia dependency '").append(step.dependencyName).append("' -> ")
        is DependencyTraceStep.ContentModule -> append(step.loadingRule.value).append(" content module ")
        DependencyTraceStep.ContainingPlugin -> append("containing plugin ")
        is DependencyTraceStep.Predefined -> append(step.reasoning)
        is DependencyTraceStep.ImpliedRpc -> append("\nvia predefined ${step.kind.id} dependency '${step.dependencyName}'")
      }
    }
    if (steps.any { it is DependencyTraceStep.ViaDependency } && !endsWith('.')) {
      append('.')
    }
  }
}
