// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.externalProcessAuthHelper.ExternalProcessHandlerService
import com.intellij.externalProcessAuthHelper.ExternalProcessRest
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import git4idea.config.GitExecutable
import git4idea.editor.GitRebaseEditorApp
import git4idea.editor.GitRebaseEditorAppHandler
import kotlinx.coroutines.CoroutineScope
import java.util.*
import kotlin.io.path.Path

@Service(Service.Level.APP)
internal class GitRebaseEditorService(
  coroutineScope: CoroutineScope
) : ExternalProcessHandlerService<GitRebaseEditorAppHandler>(
  "intellij-git-editor",
  GitRebaseEditorApp::class.java,
  GitRebaseEditorApp(),
  listOf(GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV, GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV),
  coroutineScope
) {
  companion object {
    @JvmStatic
    fun getInstance() = service<GitRebaseEditorService>()
  }

  override fun handleRequest(handler: GitRebaseEditorAppHandler, requestBody: String): String? {
    val args = requestBody.split("\n")
    if (args.size != 2) return null

    val exitCode = handler.editCommits(args[0], args[1])
    return exitCode.toString()
  }

  fun registerHandler(editorHandler: GitRebaseEditorHandler, executable: GitExecutable, disposable: Disposable): UUID {
    return registerHandler(RebaseEditorAppHandler(editorHandler, executable), disposable)
  }

  private class RebaseEditorAppHandler(private val editorHandler: GitRebaseEditorHandler,
                                       private val executable: GitExecutable) : GitRebaseEditorAppHandler {
    override fun editCommits(path: String, workingDir: String): Int {
      val file = executable.convertFilePathBack(path, Path(workingDir))
      return editorHandler.editCommits(file.toFile())
    }
  }
}

private class GitRebaseEditorExternalProcessRest : ExternalProcessRest<GitRebaseEditorAppHandler>(
  GitRebaseEditorAppHandler.ENTRY_POINT_NAME
) {
  override val externalProcessHandler: ExternalProcessHandlerService<GitRebaseEditorAppHandler> get() = GitRebaseEditorService.getInstance()
}
