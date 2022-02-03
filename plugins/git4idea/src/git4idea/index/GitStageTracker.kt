// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.runInEdt
import git4idea.GitVcs
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.status.GitRefreshListener
import git4idea.util.toShortenedLogString
import org.jetbrains.annotations.NonNls
import java.util.*

open class GitStageTracker(val project: Project) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val eventDispatcher = EventDispatcher.create(GitStageTrackerListener::class.java)
  private val dirtyScopeManager
    get() = VcsDirtyScopeManager.getInstance(project)

  @Volatile
  var state: State = State(gitRoots().associateWith { RootState.empty(it) })
    private set
  val ignoredPaths: Map<VirtualFile, List<FilePath>>
    get() {
      return gitRoots().associateWith {
        GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(it)?.ignoredFilesHolder?.ignoredFilePaths?.toList()
        ?: emptyList()
      }
    }

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        handleIndexFileEvents(events)
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt(disposableFlag) {
        val roots = gitRoots()
        update { oldState -> State(roots.associateWith { oldState.rootStates[it] ?: RootState.empty(it) }) }
      }
    })
    connection.subscribe(GitRefreshListener.TOPIC, object : GitRefreshListener {
      override fun repositoryUpdated(repository: GitRepository) {
        doUpdateState(repository)
      }
    })

    Disposer.register(this, disposableFlag)

    updateTrackerState()
  }

  fun updateTrackerState() {
    LOG.debug("Update tracker state")
    ChangeListManagerImpl.getInstanceImpl(project).executeOnUpdaterThread {
      for (root in gitRoots()) {
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(root) ?: continue
        doUpdateState(repository)
      }
    }
  }

  /**
   * Update tree on [FileDocumentManager#unsavedDocuments] state change.
   *
   * We can refresh only [doUpdateState], but this introduces blinking in some cases.
   *   Ex: when unsaved unstaged changes are saved on disk. We remove file from tree immediately,
   *   but CLM is slow to notice new saved unstaged changes - so file is removed from thee and added again in a second.
   */
  internal fun markDirty(file: VirtualFile) {
    if (!isStagingAreaAvailable()) return
    val root = getRoot(project, file) ?: return
    if (!gitRoots().contains(root)) return
    LOG.debug("Mark dirty ${file.filePath()}")
    dirtyScopeManager.fileDirty(file.filePath())
  }

  private fun handleIndexFileEvents(events: List<VFileEvent>) {
    val pathsToDirty = mutableListOf<FilePath>()
    for (event in events) {
      if (event.isFromRefresh) continue
      val file = event.file as? GitIndexVirtualFile ?: continue
      pathsToDirty.add(file.filePath)
    }

    if (pathsToDirty.isNotEmpty()) {
      LOG.debug("Mark dirty on index VFiles save: ", pathsToDirty)
      dirtyScopeManager.filePathsDirty(pathsToDirty, emptyList())
    }
  }

  private fun doUpdateState(repository: GitRepository) {
    LOG.debug("Updating ${repository.root}")

    val untracked = repository.untrackedFilesHolder.untrackedFilePaths.map { untrackedStatus(it) }
    val status = repository.stagingAreaHolder.allRecords.union(untracked).associateBy { it.path }.toMutableMap()

    for (document in FileDocumentManager.getInstance().unsavedDocuments) {
      val file = FileDocumentManager.getInstance().getFile(document) ?: continue
      if (!file.isValid || !FileDocumentManager.getInstance().isFileModified(file)) continue
      val root = getRoot(project, file) ?: continue
      if (root != repository.root) continue

      val filePath = file.filePath()
      if (repository.ignoredFilesHolder.containsFile(filePath)) continue
      val fileStatus: GitFileStatus? = status[filePath]
      if (fileStatus?.isTracked() == false) continue

      if (file is GitIndexVirtualFile && fileStatus?.getStagedStatus() == null) {
        status[filePath] = GitFileStatus('M', fileStatus?.workTree ?: ' ', filePath, fileStatus?.origPath)
      }
      else if (file.isInLocalFileSystem && fileStatus?.getUnStagedStatus() == null) {
        status[filePath] = GitFileStatus(fileStatus?.index ?: ' ', 'M', filePath, fileStatus?.origPath)
      }
    }

    val newRootState = RootState(repository.root, true, status)

    runInEdt(disposableFlag) {
      update { it.updatedWith(repository.root, newRootState) }
    }
  }

  fun addListener(listener: GitStageTrackerListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  private fun gitRoots(): List<VirtualFile> {
    return ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toList()
  }

  private fun update(updater: (State) -> State) {
    state = updater(state)
    LOG.debug("New state", state)
    eventDispatcher.multicaster.update()
  }

  protected open fun isStagingAreaAvailable() = isStagingAreaAvailable(project)

  override fun dispose() {
    state = State.EMPTY
  }

  companion object {
    private val LOG = Logger.getInstance(GitStageTracker::class.java)

    @JvmStatic
    fun getInstance(project: Project) = project.getService(GitStageTracker::class.java)

    internal fun getTrackers(): List<GitStageTracker> {
      return ProjectManager.getInstance().openProjects.mapNotNull { it.serviceIfCreated() }
    }
  }

  data class RootState(val root: VirtualFile, val initialized: Boolean,
                       val statuses: Map<FilePath, GitFileStatus>) {
    fun hasStagedFiles(): Boolean {
      return statuses.values.any { line -> line.getStagedStatus() != null }
    }

    fun hasChangedFiles(): Boolean {
      return statuses.values.any { line -> line.isTracked() }
    }

    fun hasConflictedFiles(): Boolean {
      return statuses.values.any { line -> line.isConflicted() }
    }

    fun isEmpty(): Boolean {
      return statuses.isEmpty()
    }

    @NonNls
    override fun toString(): String {
      return "RootState(root=${root.name}, statuses=${statuses.toShortenedLogString(",\n")})"
    }

    companion object {
      fun empty(root: VirtualFile) = RootState(root, false, emptyMap())
    }
  }

  data class State(val rootStates: Map<VirtualFile, RootState>) {
    val allRoots: Set<VirtualFile>
      get() = rootStates.keys
    val stagedRoots: Set<VirtualFile>
      get() = rootStates.filterValues(RootState::hasStagedFiles).keys
    val changedRoots: Set<VirtualFile>
      get() = rootStates.filterValues(RootState::hasChangedFiles).keys

    fun hasStagedRoots(): Boolean = rootStates.any { it.value.hasStagedFiles() }

    fun hasChangedRoots(): Boolean = rootStates.any { it.value.hasChangedFiles() }

    internal fun updatedWith(root: VirtualFile, newState: RootState): State {
      val result = mutableMapOf<VirtualFile, RootState>()
      result.putAll(rootStates)
      result[root] = newState
      return State(result)
    }

    @NonNls
    override fun toString(): String {
      return "State(${rootStates.toShortenedLogString(separator = "\n") { "${it.key.name}=${it.value}" }}"
    }

    companion object {
      internal val EMPTY = State(emptyMap())
    }
  }
}

private val PROCESSED = Key.create<Boolean>("GitStageTracker.file.processed")

internal open class GitStageFileDocumentManagerListener : FileDocumentManagerListener {
  protected open fun getTrackers() = GitStageTracker.getTrackers()

  override fun unsavedDocumentDropped(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    file.putUserData(PROCESSED, null)
    getTrackers().forEach { it.markDirty(file) }
  }

  override fun fileContentReloaded(file: VirtualFile, document: Document) {
    file.putUserData(PROCESSED, null)
    getTrackers().forEach { it.markDirty(file) }
  }

  override fun fileWithNoDocumentChanged(file: VirtualFile) {
    file.putUserData(PROCESSED, null)
    getTrackers().forEach { it.markDirty(file) }
  }

  override fun beforeDocumentSaving(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    file.putUserData(PROCESSED, null)
  }
}

internal open class GitStageDocumentListener : DocumentListener {
  protected open fun getTrackers() = GitStageTracker.getTrackers()

  override fun documentChanged(event: DocumentEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
    if (file.getUserData(PROCESSED) == null) {
      file.putUserData(PROCESSED, true)
      getTrackers().forEach { it.markDirty(file) }
    }
  }
}

interface GitStageTrackerListener : EventListener {
  fun update()
}

internal fun getRoot(project: Project, file: VirtualFile): VirtualFile? {
  return when {
    file is GitIndexVirtualFile -> file.root
    file.isInLocalFileSystem -> ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file)
    else -> null
  }
}

fun GitStageTracker.status(file: VirtualFile): GitFileStatus? {
  val root = getRoot(project, file) ?: return null
  return status(root, file)
}

fun GitStageTracker.status(root: VirtualFile, file: VirtualFile): GitFileStatus? {
  val filePath = file.filePath()

  if (GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root)?.ignoredFilesHolder?.containsFile(filePath) == true) {
    return ignoredStatus(filePath)
  }
  val rootState = state.rootStates[root] ?: return null
  if (!rootState.initialized) return null
  return rootState.statuses[filePath] ?: return notChangedStatus(filePath)
}