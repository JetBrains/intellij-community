// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.FrameStateListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.BaseSingleTaskController
import com.intellij.vcsUtil.VcsUtil
import git4idea.config.GitExecutableManager
import git4idea.index.getFileStatus
import java.util.*

internal class LightGitTracker : Disposable {
  private val lightEditService = LightEditService.getInstance()
  private val lightEditorManager = lightEditService.editorManager
  private val eventDispatcher = EventDispatcher.create(LightGitTrackerListener::class.java)
  private val singleTaskController = MySingleTaskController()
  private val listener = MyLightEditorListener()

  private val gitExecutable: String
    get() = GitExecutableManager.getInstance().pathToGit

  @Volatile
  private var state: State = State()
  val currentLocation: String?
    get() = state.location
  val statuses: Map<VirtualFile, FileStatus>
    get() = state.statuses

  init {
    lightEditorManager.addListener(listener, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(FrameStateListener.TOPIC,
                                                                           MyFrameStateListener())
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES,
                                                                           MyBulkFileListener())
  }

  fun getFileStatus(file: VirtualFile): FileStatus {
    return state.statuses[file] ?: FileStatus.NOT_CHANGED
  }

  private fun updateCurrentState(s: State) {
    val statusesMap = lightEditorManager.openFiles.associateWith {
      s.statuses[it] ?: state.statuses[it] ?: FileStatus.NOT_CHANGED
    }
    state = s.copy(statuses = statusesMap)
    eventDispatcher.multicaster.update()
    if (s.statuses.isNotEmpty()) lightEditService.updateFileStatus(s.statuses.keys)
  }

  fun addUpdateListener(listener: LightGitTrackerListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  override fun dispose() {
  }

  private inner class MyBulkFileListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      val targetFiles = events.filter { it.isFromSave || it.isFromRefresh }.mapNotNullTo(mutableSetOf()) { it.file }
      val lightTargetFiles = lightEditorManager.openFiles.intersect(targetFiles)
      if (lightTargetFiles.isNotEmpty()) {
        singleTaskController.request(Request.Status(lightTargetFiles))
      }
    }
  }

  private inner class MyFrameStateListener : FrameStateListener {
    override fun onFrameActivated() {
      val requests = mutableListOf<Request>()

      val selectedFile = lightEditService.selectedFile
      if (selectedFile != null) {
        requests.add(Request.Location(selectedFile))
      }

      val openFiles = lightEditorManager.openFiles
      if (openFiles.isNotEmpty()) {
        requests.add(Request.Status(openFiles))
      }

      if (requests.isNotEmpty()) {
        singleTaskController.request(*requests.toTypedArray())
      }
    }
  }

  private inner class MyLightEditorListener : LightEditorListener {
    override fun afterSelect(editorInfo: LightEditorInfo?) {
      state = state.copy(location = null)

      val selectedFile = editorInfo?.file
      if (selectedFile != null) {
        singleTaskController.request(Request.Location(selectedFile))
      }
    }

    override fun afterClose(editorInfo: LightEditorInfo) {
      state = state.copy(statuses = state.statuses.without(editorInfo.file))
    }
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<Request, State>("Light Git Tracker", this::updateCurrentState, this) {
    override fun process(requests: List<Request>, previousResult: State?): State {
      val locationFile = requests.lastInstance(Request.Location::class.java)?.file
      val files = requests.filterIsInstance(Request.Status::class.java).flatMapTo(mutableSetOf()) { it.files }

      val location: String? = locationFile?.let {
        try {
          getLocation(it.parent, gitExecutable)
        }
        catch (_: VcsException) {
          null
        }
      }

      val statuses = mutableMapOf<VirtualFile, FileStatus>()
      previousResult?.statuses?.let { statuses.putAll(it) }
      for (file in files) {
        try {
          if (file != locationFile || location != null) {
            statuses[file] = getFileStatus(file.parent, VcsUtil.getFilePath(file), gitExecutable)
          }
        }
        catch (_: VcsException) {
        }
      }

      return State(location, statuses)
    }
  }

  private data class State(val location: String?, val statuses: Map<VirtualFile, FileStatus>) {
    constructor() : this(null, emptyMap())
  }

  private sealed class Request {
    class Location(val file: VirtualFile) : Request()
    class Status(val files: Collection<VirtualFile>) : Request()
  }

  companion object {
    fun getInstance(): LightGitTracker {
      return ServiceManager.getService(LightGitTracker::class.java)
    }
  }
}

interface LightGitTrackerListener : EventListener {
  fun update()
}

fun <K, V> Map<K, V>.without(removed: K): Map<K, V> {
  val result = this.toMutableMap()
  result.remove(removed)
  return result
}

fun <R> List<*>.lastInstance(klass: Class<R>): R? {
  val iterator = this.listIterator(size)
  while (iterator.hasPrevious()) {
    val element = iterator.previous()
    @Suppress("UNCHECKED_CAST")
    if (klass.isInstance(element)) return element as R
  }
  return null
}