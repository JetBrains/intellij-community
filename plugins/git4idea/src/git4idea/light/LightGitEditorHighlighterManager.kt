// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorInfoImpl
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.BaseSingleTaskController
import git4idea.index.isTracked
import git4idea.index.repositoryPath
import org.jetbrains.annotations.NonNls

class LightGitEditorHighlighterManager(val tracker: LightGitTracker) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val singleTaskController = MySingleTaskController()
  private var lst: SimpleLocalLineStatusTracker? = null

  private val lightEditService
    get() = LightEditService.getInstance()

  init {
    tracker.addUpdateListener(object : LightGitTrackerListener {
      override fun update() {
        lightEditService.selectedFileEditor?.let { fileEditor -> updateLst(fileEditor) }
      }
    }, this)
    lightEditService.editorManager.addListener(object : LightEditorListener {
      override fun afterSelect(editorInfo: LightEditorInfo?) {
        if (editorInfo == null) {
          dropLst()
          return
        }
        if (editorInfo.file != lst?.virtualFile) {
          dropLst()
          updateLst(editorInfo.fileEditor)
        }
      }
    }, this)

    Disposer.register(tracker, this)
    Disposer.register(this, disposableFlag)
  }

  private fun readBaseVersion(file: VirtualFile, repositoryPath: String?) {
    if (repositoryPath == null) {
      lst?.setBaseRevision("")
      return
    }

    singleTaskController.request(Request(file, repositoryPath))
  }

  private fun setBaseVersion(baseVersion: BaseVersion) {
    if (lightEditService.selectedFile == baseVersion.file && lst?.virtualFile == baseVersion.file) {
      if (baseVersion.text != null) {
        lst?.setBaseRevision(baseVersion.text)
      } else {
        dropLst()
      }
    }
  }

  private fun updateLst(fileEditor: FileEditor) {
    val editor = LightEditorInfoImpl.getEditor(fileEditor)
    val file = fileEditor.file

    if (editor == null || file == null) {
      dropLst()
      return
    }

    val status = tracker.getFileStatus(file)
    if (!status.isTracked()) {
      dropLst()
      return
    }

    if (lst == null) {
      lst = SimpleLocalLineStatusTracker.createTracker(lightEditService.project!!, editor.document, file)
    }
    readBaseVersion(file, status.repositoryPath)
  }

  private fun dropLst() {
    lst?.release()
    lst = null
  }

  override fun dispose() {
    dropLst()
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<Request, BaseVersion>("light.highlighter", this::setBaseVersion, disposableFlag) {
    override fun process(requests: List<Request>, previousResult: BaseVersion?): BaseVersion {
      val request = requests.last()
      try {
        val content = getFileContentAsString(request.file, request.repositoryPath, tracker.gitExecutable)
        return BaseVersion(request.file, StringUtil.convertLineSeparators(content))
      } catch (e: VcsException) {
        LOG.warn("Could not read base version for ${request.file}", e)
        return BaseVersion(request.file, null)
      }
    }

    override fun cancelRunningTasks(requests: List<Request>): Boolean = true
  }

  private data class Request(val file: VirtualFile, val repositoryPath: String)
  private data class BaseVersion(val file: VirtualFile, val text: String?) {
    override fun toString(): @NonNls String {
      return "BaseVersion(file=$file, text=${text?.take(10) ?: "null"}"
    }
  }
}
