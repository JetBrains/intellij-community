// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp

import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.recognizeSplitModeModuleKind
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal suspend fun recognizeSplitModeModuleKindForPath(descriptorPath: String): DevKitMcpToolset.RecognizeIjModuleKindResult {
  val project = currentCoroutineContext().project
  val resolvedDescriptorPath = project.resolveInProject(descriptorPath)
  val descriptorVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(resolvedDescriptorPath)
                             ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(resolvedDescriptorPath)
                             ?: mcpFail("File not found: $descriptorPath")

  val service = SplitModeApiRestrictionsService.getInstance(project)
  service.scheduleLoadRestrictions()
  withTimeoutOrNull(5.seconds) {
    while (!service.isLoaded()) {
      delay(50.milliseconds)
    }
  } ?: mcpFail("Timed out waiting for split-mode module restrictions to load")

  val recognizedKind = readAction {
    val psiFile = PsiManager.getInstance(project).findFile(descriptorVirtualFile)
                  ?: mcpFail("Psi file not found: $descriptorPath")
    recognizeSplitModeModuleKind(psiFile)
      ?: mcpFail("Descriptor is not an IntelliJ plugin XML file or no IntelliJ module was found: $descriptorPath")
  }

  return DevKitMcpToolset.RecognizeIjModuleKindResult(
    moduleName = recognizedKind.moduleName,
    descriptorPath = descriptorPath,
    kindId = recognizedKind.kind.id,
    reasoning = recognizedKind.reasoning,
  )
}
