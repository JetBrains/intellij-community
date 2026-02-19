// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress
import org.jetbrains.kotlin.idea.configuration.withScope
import org.jetbrains.kotlin.idea.inspections.libraries.AddKotlinLibraryQuickFix

internal object AddReflectionQuickFixFactory {
  private const val GROUP_ID = "org.jetbrains.kotlin"
  private const val ARTIFACT_ID = "kotlin-reflect"

  val addReflectionQuickFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NoReflectionInClassPath ->
    val element = diagnostic.psi
    val module = element.module ?: return@IntentionBased emptyList()

    val dependencyManager = KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)
      ?: return@IntentionBased emptyList()
    if (dependencyManager.isProjectSyncPendingOrInProgress()) return@IntentionBased emptyList()

    val version = KotlinLibraryVersionProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
      it.getVersion(module, GROUP_ID, ARTIFACT_ID)
    } ?: KotlinPluginLayout.standaloneCompilerVersion.artifactVersion

    val virtualFile = element.containingFile.virtualFile
    val scope = if (virtualFile != null && ProjectFileIndex.getInstance(module.project).isInTestSourceContent(virtualFile)) {
      DependencyScope.TEST
    } else {
      DependencyScope.COMPILE
    }

    val libraryDescriptor = ExternalLibraryDescriptor(GROUP_ID, ARTIFACT_ID, version, version, version)
      .withScope(scope)

      listOf(
          AddKotlinLibraryQuickFix(
              dependencyManager = dependencyManager,
              libraryDescriptor = libraryDescriptor,
              quickFixText = KotlinBundle.message("add.kotlin.reflect.library")
          )
      )
  }
}