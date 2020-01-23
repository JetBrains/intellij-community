// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.ide.FrameStateListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditorInfo
import com.intellij.ide.lightEdit.LightEditorListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.BaseSingleTaskController
import com.intellij.vcs.log.runInEdt
import com.intellij.vcsUtil.VcsUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionIdentificationException
import git4idea.index.GitFileStatus
import git4idea.index.getFileStatus
import java.util.*

class LightGitTracker : Disposable {
  private val lightEditService = LightEditService.getInstance()
  private val lightEditorManager = lightEditService.editorManager
  private val eventDispatcher = EventDispatcher.create(LightGitTrackerListener::class.java)
  private val singleTaskController = MySingleTaskController()
  private val listener = MyLightEditorListener()

  private val highlighterManager: LightGitEditorHighlighterManager

  val gitExecutable: String
    get() = GitExecutableManager.getInstance().pathToGit

  @Volatile
  private var hasGit: Boolean = false

  @Volatile
  private var state: State = State.Blank
  val currentLocation: String?
    get() = state.location
  val statuses: Map<VirtualFile, GitFileStatus>
    get() = state.statuses

  init {
    lightEditorManager.addListener(listener, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(FrameStateListener.TOPIC,
                                                                           MyFrameStateListener())
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES,
                                                                           MyBulkFileListener())

    highlighterManager = LightGitEditorHighlighterManager(this)

    singleTaskController.request(Request.CheckGit)
  }

  fun getFileStatus(file: VirtualFile): GitFileStatus {
    return state.statuses[file] ?: GitFileStatus.Blank
  }

  private fun updateCurrentState(updater: StateUpdater) {
    when (updater) {
      StateUpdater.Clear -> {
        val previousStatuses = state.statuses
        val statusesChanged = previousStatuses.isNotEmpty() && previousStatuses.values.any { it != GitFileStatus.Blank }
        val changed = statusesChanged || !(state.location.isNullOrBlank())

        state = State.Blank

        if (changed) eventDispatcher.multicaster.update()
        if (statusesChanged) lightEditService.updateFileStatus(previousStatuses.keys)
      }
      is StateUpdater.Update -> {
        val newState = updater.state
        val statusesMap = lightEditorManager.openFiles.associateWith {
          newState.statuses[it] ?: state.statuses[it] ?: GitFileStatus.Blank
        }
        val location = if (updater.updateLocation) newState.location else state.location

        state = State(location, statusesMap)

        eventDispatcher.multicaster.update()
        if (newState.statuses.isNotEmpty()) lightEditService.updateFileStatus(newState.statuses.keys)
      }
    }
  }

  private fun checkGit() {
    try {
      val version = GitExecutableManager.getInstance().identifyVersion(gitExecutable)
      hasGit = version.isSupported
    }
    catch (e: GitVersionIdentificationException) {
      hasGit = false
    }
  }

  fun addUpdateListener(listener: LightGitTrackerListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  override fun dispose() {
  }

  private inner class MyBulkFileListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (!hasGit) return

      val targetFiles = events.filter { it.isFromSave || it.isFromRefresh }.mapNotNullTo(mutableSetOf()) { it.file }
      val lightTargetFiles = lightEditorManager.openFiles.intersect(targetFiles).filter { it.parent != null }
      if (lightTargetFiles.isNotEmpty()) {
        singleTaskController.request(Request.Status(lightTargetFiles))
      }
    }
  }

  private inner class MyFrameStateListener : FrameStateListener {
    override fun onFrameActivated() {
      val requests = mutableListOf<Request>(Request.CheckGit)

      val selectedFile = lightEditService.selectedFile
      if (selectedFile != null && selectedFile.parent != null) {
        requests.add(Request.Location(selectedFile))
      }

      val openFiles = lightEditorManager.openFiles.filter { it.parent != null }
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
      if (!hasGit) return

      state = state.copy(location = null)

      val selectedFile = editorInfo?.file
      if (selectedFile != null && selectedFile.parent != null) {
        singleTaskController.request(Request.Location(selectedFile))
      }
      else {
        runInEdt(this@LightGitTracker) { eventDispatcher.multicaster.update() }
      }
    }

    override fun afterClose(editorInfo: LightEditorInfo) {
      state = state.copy(statuses = state.statuses.without(editorInfo.file))
    }
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<Request, StateUpdater>("Light Git Tracker", this::updateCurrentState, this) {
    override fun process(requests: List<Request>, previousResult: StateUpdater?): StateUpdater {
      if (requests.contains(Request.CheckGit)) {
        checkGit()
      }
      if (!hasGit) return StateUpdater.Clear

      val locationFile = requests.lastInstance(Request.Location::class.java)?.file
      val files = requests.filterIsInstance(Request.Status::class.java).flatMapTo(mutableSetOf()) { it.files }

      val location: String? = if (locationFile != null) {
        try {
          getLocation(locationFile.parent, gitExecutable)
        }
        catch (_: VcsException) {
          null
        }
      }
      else previousResult?.state?.location
      val updateLocation = locationFile != null || (previousResult as? StateUpdater.Update)?.updateLocation ?: false

      val statuses = mutableMapOf<VirtualFile, GitFileStatus>()
      previousResult?.state?.statuses?.let { statuses.putAll(it) }
      for (file in files) {
        try {
          if (file != locationFile || location != null) {
            statuses[file] = getFileStatus(file.parent, VcsUtil.getFilePath(file), gitExecutable)
          }
        }
        catch (_: VcsException) {
        }
      }

      return StateUpdater.Update(State(location, statuses), updateLocation)
    }
  }

  private data class State(val location: String?, val statuses: Map<VirtualFile, GitFileStatus>) {
    private constructor() : this(null, emptyMap())

    companion object {
      val Blank = State()
    }
  }

  private sealed class StateUpdater(val state: State) {
    object Clear : StateUpdater(State.Blank)
    class Update(s: State, val updateLocation: Boolean) : StateUpdater(s)
  }

  private sealed class Request {
    class Location(val file: VirtualFile) : Request()
    class Status(val files: Collection<VirtualFile>) : Request()
    object CheckGit : Request()
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