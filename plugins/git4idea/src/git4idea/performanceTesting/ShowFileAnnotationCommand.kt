// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.AnnotateToggleAction
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.vcsUtil.VcsUtil
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShowFileAnnotationCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "showFileAnnotation"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val editor = EditorFactoryImpl.getInstance().allEditors
    if (editor.isEmpty() || editor.size > 1) throw RuntimeException("Expected only one editor can be opened at the moment")

    val file = FileDocumentManager.getInstance().getFile(editor[0].getDocument())
    if (file == null) throw RuntimeException("No open file found")

    val vcsFile = VcsUtil.resolveSymlinkIfNeeded(context.project, file)
    val vcs: AbstractVcs? = ProjectLevelVcsManager.getInstance(context.project).getVcsFor(vcsFile)
    if (vcs == null) throw RuntimeException("Vcs subsystem wasn't initialized for some reason")

    val annotationProvider = vcs.annotationProvider
    if (annotationProvider == null) throw RuntimeException("Annotation provider wasn't initialized for some reason")

    val annotation = annotationProvider.annotate(vcsFile)
    withContext(Dispatchers.EDT) {
      PerformanceTestSpan.TRACER.spanBuilder(NAME).use {
        AnnotateToggleAction.doAnnotate(editor[0], context.project, annotation, vcs)
      }
    }

  }

  override fun getName() = NAME

}