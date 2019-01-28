// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.branch.GitRebaseParams
import git4idea.commands.*
import git4idea.push.GitPushParams
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEditorService
import git4idea.repo.GitRepository
import java.io.File

/**
 * Any unknown error that could be returned by Git.
 */
const val UNKNOWN_ERROR_TEXT: String = "unknown error"

class TestGitImpl : GitImpl() {
  private val LOG = Logger.getInstance(TestGitImpl::class.java)

  @Volatile var stashListener: ((GitRepository) -> Unit)? = null
  @Volatile var mergeListener: ((GitRepository) -> Unit)? = null
  @Volatile var pushListener: ((GitRepository) -> Unit)? = null

  @Volatile private var rebaseShouldFail: (GitRepository) -> Boolean = { false }
  @Volatile private var pushHandler: (GitRepository) -> GitCommandResult? = { null }
  @Volatile private var branchDeleteHandler: (GitRepository) -> GitCommandResult? = { null }
  @Volatile private var checkoutNewBranchHandler: (GitRepository) -> GitCommandResult? = { null }
  @Volatile private var interactiveRebaseEditor: InteractiveRebaseEditor? = null

  class InteractiveRebaseEditor(val entriesEditor: ((String) -> String)?,
                                val plainTextEditor: ((String) -> String)?)

  override fun push(repository: GitRepository,
                    pushParams: GitPushParams,
                    vararg listeners: GitLineHandlerListener): GitCommandResult {
    pushListener?.invoke(repository)
    return pushHandler(repository) ?: super.push(repository, pushParams, *listeners)
  }

  override fun checkoutNewBranch(repository: GitRepository, branchName: String, listener: GitLineHandlerListener?): GitCommandResult {
    return checkoutNewBranchHandler(repository) ?: super.checkoutNewBranch(repository, branchName, listener)
  }

  override fun branchDelete(repository: GitRepository,
                            branchName: String,
                            force: Boolean,
                            vararg listeners: GitLineHandlerListener?): GitCommandResult {
    return branchDeleteHandler(repository) ?: super.branchDelete(repository, branchName, force, *listeners)
  }

  override fun rebase(repository: GitRepository, params: GitRebaseParams, vararg listeners: GitLineHandlerListener): GitRebaseCommandResult {
    return failOrCallRebase(repository) {
      super.rebase(repository, params, *listeners)
    }
  }

  override fun rebaseAbort(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitRebaseCommandResult {
    return failOrCallRebase(repository) {
      super.rebaseAbort(repository, *listeners)
    }
  }

  override fun rebaseContinue(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitRebaseCommandResult {
    return failOrCallRebase(repository) {
      super.rebaseContinue(repository, *listeners)
    }
  }

  override fun rebaseSkip(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitRebaseCommandResult {
    return failOrCallRebase(repository) {
      super.rebaseSkip(repository, *listeners)
    }
  }

  override fun createEditor(project: Project, root: VirtualFile, handler: GitLineHandler,
                            commitListAware: Boolean): GitInteractiveRebaseEditorHandler {
    if (interactiveRebaseEditor == null) return super.createEditor(project, root, handler, commitListAware)

    val service = GitRebaseEditorService.getInstance()
    val editor = object: GitInteractiveRebaseEditorHandler(service, project, root) {
      override fun handleUnstructuredEditor(path: String): Boolean {
        val plainTextEditor = interactiveRebaseEditor!!.plainTextEditor
        return if (plainTextEditor != null) handleEditor(path, plainTextEditor) else super.handleUnstructuredEditor(path)
      }

      override fun handleInteractiveEditor(path: String): Boolean {
        val entriesEditor = interactiveRebaseEditor!!.entriesEditor
        return if (entriesEditor != null) handleEditor(path, entriesEditor) else super.handleInteractiveEditor(path)
      }

      private fun handleEditor(path: String, editor: (String) -> String): Boolean {
        try {
          val file = File(path)
          FileUtil.writeToFile(file, editor(FileUtil.loadFile(file)))
        }
        catch(e: Exception) {
          LOG.error(e)
        }
        return true
      }
    }
    service.configureHandler(handler, editor.handlerNo)
    return editor
  }

  override fun stashSave(repository: GitRepository, message: String): GitCommandResult {
    stashListener?.invoke(repository)
    return  super.stashSave(repository, message)
  }

  override fun merge(repository: GitRepository,
                     branchToMerge: String,
                     additionalParams: MutableList<String>?,
                     vararg listeners: GitLineHandlerListener?): GitCommandResult {
    mergeListener?.invoke(repository)
    return super.merge(repository, branchToMerge, additionalParams, *listeners)
  }

  fun setShouldRebaseFail(shouldFail: (GitRepository) -> Boolean) {
    rebaseShouldFail = shouldFail
  }

  fun onPush(handler: (GitRepository) -> GitCommandResult?) {
    pushHandler = handler
  }

  fun onCheckoutNewBranch(handler: (GitRepository) -> GitCommandResult?) {
    checkoutNewBranchHandler = handler
  }

  fun onBranchDelete(handler: (GitRepository) -> GitCommandResult?) {
    branchDeleteHandler = handler
  }

  fun setInteractiveRebaseEditor(editor: InteractiveRebaseEditor) {
    interactiveRebaseEditor = editor
  }

  fun reset() {
    rebaseShouldFail = { false }
    pushHandler = { null }
    checkoutNewBranchHandler = { null }
    branchDeleteHandler = { null }
    interactiveRebaseEditor = null
    pushListener = null
    stashListener = null
    mergeListener = null
  }

  private fun failOrCallRebase(repository: GitRepository, delegate: () -> GitRebaseCommandResult): GitRebaseCommandResult {
    return if (rebaseShouldFail(repository)) {
      GitRebaseCommandResult.normal(fatalResult())
    }
    else {
      delegate()
    }
  }

  private fun fatalResult() = GitCommandResult(false, 128, listOf("fatal: error: $UNKNOWN_ERROR_TEXT"), emptyList<String>())
}


