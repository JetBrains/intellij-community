/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.test

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.branch.GitRebaseParams
import git4idea.commands.GitCommandResult
import git4idea.commands.GitImpl
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEditorService
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import java.io.File

/**
 * Any unknown error that could be returned by Git.
 */
val UNKNOWN_ERROR_TEXT: String = "unknown error"

class TestGitImpl : GitImpl() {
  private val LOG = Logger.getInstance(TestGitImpl::class.java)

  @Volatile private var myRebaseShouldFail: (GitRepository) -> Boolean = { false }
  @Volatile private var myPushHandler: (GitRepository) -> GitCommandResult? = { null }
  @Volatile private var myInteractiveRebaseEditor: ((String) -> String)? = null

  override fun push(repository: GitRepository,
                    remote: GitRemote,
                    spec: String,
                    force: Boolean,
                    updateTracking: Boolean,
                    tagMode: String?,
                    vararg listeners: GitLineHandlerListener): GitCommandResult {
    return myPushHandler(repository) ?:
        super.push(repository, remote, spec, force, updateTracking, tagMode, *listeners)
  }

  override fun rebase(repository: GitRepository, params: GitRebaseParams, vararg listeners: GitLineHandlerListener): GitCommandResult {
    return failOrCall(repository) {
      super.rebase(repository, params, *listeners)
    }
  }

  override fun rebaseAbort(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitCommandResult {
    return failOrCall(repository) {
      super.rebaseAbort(repository, *listeners)
    }
  }

  override fun rebaseContinue(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitCommandResult {
    return failOrCall(repository) {
      super.rebaseContinue(repository, *listeners)
    }
  }

  override fun rebaseSkip(repository: GitRepository, vararg listeners: GitLineHandlerListener?): GitCommandResult {
    return failOrCall(repository) {
      super.rebaseSkip(repository, *listeners)
    }
  }

  override fun configureEditor(project: Project, root: VirtualFile, handler: GitLineHandler,
                               commitListAware: Boolean): GitInteractiveRebaseEditorHandler {
    if (myInteractiveRebaseEditor == null) return super.configureEditor(project, root, handler, commitListAware)

    val service = GitRebaseEditorService.getInstance()
    val editor = object: GitInteractiveRebaseEditorHandler(service, project, root, handler) {
      override fun editCommits(path: String?): Int {
        try {
          val file = File(path)
          FileUtil.writeToFile(file, myInteractiveRebaseEditor!!(FileUtil.loadFile(file)))
        }
        catch(e: Exception) {
          LOG.error(e)
          return 1
        }
        return 0
      }
    }
    service.configureHandler(handler, editor.handlerNo)
    return editor
  }

  fun setShouldRebaseFail(shouldFail: (GitRepository) -> Boolean) {
    myRebaseShouldFail = shouldFail
  }

  fun onPush(pushHandler: (GitRepository) -> GitCommandResult?) {
    myPushHandler = pushHandler;
  }

  fun setInteractiveRebaseEditor(editor: (String) -> String) {
    myInteractiveRebaseEditor = editor
  }

  fun reset() {
    myRebaseShouldFail = { false }
    myPushHandler = { null }
    myInteractiveRebaseEditor = null
  }

  private fun failOrCall(repository: GitRepository, delegate: () -> GitCommandResult): GitCommandResult {
    return if (myRebaseShouldFail(repository)) {
      fatalResult()
    }
    else {
      delegate()
    }
  }

  private fun fatalResult() = GitCommandResult(false, 128, listOf("fatal: error: $UNKNOWN_ERROR_TEXT"), emptyList<String>(), null)
}


