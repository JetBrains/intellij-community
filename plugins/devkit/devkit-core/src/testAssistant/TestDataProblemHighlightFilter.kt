// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

/**
 * Filter that turns off highlighting for a file under a `testData` directory outside a source root,
 * in an IntelliJ Platform or plugin project. A resource root counts as outside.
 *
 * The daemon creates no highlighting pass for such a file, so [TestDataHighlightingPass] does not color `<caret>` in it.
 */
internal class TestDataProblemHighlightFilter : ProblemHighlightFilter() {
  override fun shouldHighlight(psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile ?: return true
    val project = psiFile.project
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      // The project types are empty in dumb mode, and isIntelliJPluginProject caches its first answer.
      if (DumbService.isDumb(project) || !IntelliJProjectUtil.isIntelliJPluginProject(project)) {
        return true
      }
    }
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val contentRoot = fileIndex.getContentRootForFile(virtualFile, false) ?: return true
    if (!TestDataDirectories.isInTestDataDirectory(virtualFile, contentRoot)) {
      return true
    }
    return fileIndex.isInSourceContent(virtualFile) && !fileIndex.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.RESOURCES)
  }
}
