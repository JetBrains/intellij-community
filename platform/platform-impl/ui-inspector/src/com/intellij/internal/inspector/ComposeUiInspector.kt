// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.compose.swing.ComposeSwingPanel
import com.intellij.pom.Navigatable
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ClientProperty
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.ui.EDT
import org.jetbrains.compose.swing.core.findRecomposer
import org.jetbrains.compose.swing.tooling.attachComposeStackTrace
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import java.awt.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.swing.JComponent

/**
 * Reads declaring composables from the slot table.
 * `Container.add` stack traces show runtime frames, not the code that declared the component.
 */
internal object ComposeUiInspector {
  /** The property row for components declared by Compose. */
  const val ADDED_AT_ROW: String = "added-at (Compose)"

  private val COMPOSE_FRAME = Pattern.compile("\\bat ([^()./\\\\]+)\\(([^()\\s/\\\\:]+\\.[A-Za-z]+):(\\d+)\\)")

  @JvmRecord
  data class ComposeStackTrace(
    val text: String,
    val resolvedLines: CompletableFuture<List<ComposeStackTraceLine>>,
  ) {
    override fun toString(): String = text
  }

  @JvmRecord
  data class ComposeStackTraceLine(
    val text: String,
    val linkStartOffset: Int,
    val linkEndOffset: Int,
    val hyperlink: HyperlinkInfo?,
    val isOutsideComposeSwingModule: Boolean,
  )

  /**
   * Enables process-wide source and ownership recording.
   *
   * Existing compositions must rebuild because their stack trace mode cannot change. The rebuild replaces their components.
   * Call this on the EDT before tree collection. The devkit action keeps its toggle synchronized.
   * Recording stays enabled because existing data needs it.
   */
  fun startRecording() {
    composeOrNull {
      enableDiagnosticStackTraces()
      isDebugInspectorInfoEnabled = true
    }
  }

  private fun enableDiagnosticStackTraces() {
    val action = ActionManager.getInstance().getAction("ComposeVerboseStackTrace") as? ToggleAction
    if (action == null) {
      Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
      return
    }
    val event = AnActionEvent.createEvent(
      action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null,
    )
    action.setSelected(event, true)
  }

  /** Returns whether ownership recording is enabled. */
  val isRecording: Boolean
    get() = composeOrNull { isDebugInspectorInfoEnabled } == true

  /**
   * Returns whether [component] belongs to Compose content.
   *
   * During recording, only components declared by Compose match. Before recording, a recomposer match lets the inspector offer recording.
   */
  fun isComposeComponent(component: Component): Boolean {
    if (!EDT.isCurrentThreadEdt()) return false
    return composeOrNull {
      if (isDebugInspectorInfoEnabled) component.findDeclaringGroup() != null
      else component.findRecomposer() != null
    } == true
  }

  /** Returns the legacy creation stack trace. */
  fun creationStackTrace(component: JComponent): Throwable? =
    ClientProperty.get(component, ComposeSwingPanel.CREATION_STACKTRACE)

  /**
   * Returns the declaring composables nearest first.
   * Returns `null` if recording, rebuilding, declaration, or compiler source data is absent.
   */
  private fun declaringComposables(component: Component): String? {
    // Off-EDT reads race with recomposition writes to the slot table.
    if (!EDT.isCurrentThreadEdt()) return null
    return composeOrNull { readDeclaringComposables(component) }
  }

  fun stackTrace(component: Component): ComposeStackTrace? {
    val text = declaringComposables(component) ?: return null
    val project = ProjectUtil.getProjectForComponent(component)
    val lines = CompletableFuture.supplyAsync(
      {
        ReadAction.nonBlocking<List<ComposeStackTraceLine>> {
          DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode<List<ComposeStackTraceLine>, RuntimeException> {
            resolveStackTraceLines(text, project)
          }
        }.executeSynchronously()
      },
      AppExecutorUtil.getAppExecutorService(),
    )
    return ComposeStackTrace(text, lines)
  }

  fun createSourceNavigatable(
    stackTrace: ComposeStackTrace,
    project: Project,
    modalityComponent: Component,
    fallback: Navigatable,
  ): Navigatable = object : Navigatable {
    override fun navigate(requestFocus: Boolean) {
      navigateToSource(stackTrace, project, modalityComponent) { fallback.navigate(requestFocus) }
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true
  }

  fun navigateToSource(stackTrace: ComposeStackTrace, project: Project?, modalityComponent: Component) {
    if (project != null) navigateToSource(stackTrace, project, modalityComponent, null)
  }

  private fun navigateToSource(
    stackTrace: ComposeStackTrace,
    project: Project,
    modalityComponent: Component,
    fallback: (() -> Unit)?,
  ) {
    val lines = stackTrace.resolvedLines.getNow(null)
    if (lines != null) {
      if (!navigateToFirstSourceFrame(lines, project)) fallback?.invoke()
      return
    }

    val modalityState = ModalityState.stateForComponent(modalityComponent)
    stackTrace.resolvedLines.thenAccept { resolvedLines ->
      ApplicationManager.getApplication().invokeLater(
        { if (!navigateToFirstSourceFrame(resolvedLines, project)) fallback?.invoke() },
        modalityState,
      )
    }
  }

  private fun navigateToFirstSourceFrame(lines: List<ComposeStackTraceLine>, project: Project): Boolean {
    val first = lines.firstOrNull { it.hyperlink != null && it.isOutsideComposeSwingModule }
                ?: lines.firstOrNull { it.hyperlink != null }
                ?: return false
    first.hyperlink?.navigate(project)
    return true
  }

  private fun resolveStackTraceLines(text: String, project: Project?): List<ComposeStackTraceLine> {
    val resolvedFiles = HashMap<String, ResolvedComposeFile>()
    val lines = ArrayList<ComposeStackTraceLine>()
    for (line in Pattern.compile("\\n").split(text, -1)) {
      val match = COMPOSE_FRAME.matcher(line)
      if (!match.find()) {
        lines.add(ComposeStackTraceLine(line, -1, -1, null, false))
        continue
      }

      val resolvedFile = resolvedFiles.computeIfAbsent(match.group(2)) { resolveComposeFile(it, project) }
      val lineNumber = match.group(3).toInt() - 1
      val hyperlink = createComposeHyperlink(resolvedFile.files, lineNumber, project)
      lines.add(
        ComposeStackTraceLine(
          line,
          match.start(2),
          match.end(3),
          hyperlink,
          resolvedFile.isOutsideComposeSwingModule,
        ),
      )
    }
    return lines.toList()
  }

  private fun resolveComposeFile(fileName: String, project: Project?): ResolvedComposeFile {
    if (project == null || project.isDisposed) return ResolvedComposeFile(emptyList(), false)
    val projectFiles = findComposeFiles(fileName, GlobalSearchScope.projectScope(project))
    if (projectFiles.isNotEmpty()) {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      val isOutsideComposeSwingModule = projectFiles.any { file ->
        val module = fileIndex.getModuleForFile(file, false)
        module != null && !module.name.endsWith(".compose.swing")
      }
      return ResolvedComposeFile(projectFiles, isOutsideComposeSwingModule)
    }
    return ResolvedComposeFile(findComposeFiles(fileName, GlobalSearchScope.allScope(project)), false)
  }

  private fun findComposeFiles(fileName: String, scope: GlobalSearchScope): List<VirtualFile> =
    FilenameIndex.getVirtualFilesByName(fileName, scope).toList()

  private fun createComposeHyperlink(files: List<VirtualFile>, lineNumber: Int, project: Project?): HyperlinkInfo? {
    if (project == null || files.isEmpty()) return null
    return if (files.size == 1) {
      OpenFileHyperlinkInfo(project, files.first(), lineNumber)
    }
    else {
      HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(files, lineNumber, project)
    }
  }

  private data class ResolvedComposeFile(
    val files: List<VirtualFile>,
    val isOutsideComposeSwingModule: Boolean,
  )

  private fun readDeclaringComposables(component: Component): String? {
    val probe = Throwable()
    // The return value distinguishes a trace from a suppressed collection failure.
    if (!component.attachComposeStackTrace(probe)) return null
    val trace = probe.suppressedExceptions.last().message
    return trace?.ifEmpty { null }
  }
}

/**
 * Returns `null` if Compose is absent or rejects tooling access.
 * Missing Compose classes raise [LinkageError]. Logs once because this runs for each component.
 */
internal fun <T> composeOrNull(read: () -> T): T? =
  try {
    read()
  }
  catch (e: Throwable) {
    if (e is ProcessCanceledException) throw e
    if (reported.compareAndSet(false, true)) {
      logger<ComposeUiInspector>().warn("The UI inspector cannot read Compose Swing UI compositions", e)
    }
    null
  }

private val reported = AtomicBoolean(false)
