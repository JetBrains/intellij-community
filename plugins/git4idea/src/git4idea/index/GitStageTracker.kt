// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.AppTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.BaseSingleTaskController
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.sendRequests
import git4idea.GitVcs
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitUntrackedFilesHolder
import git4idea.util.toMapOfSets
import git4idea.util.toShortenedString
import java.util.*
import kotlin.collections.HashSet

class GitStageTracker(val project: Project) : Disposable {
  private val eventDispatcher = EventDispatcher.create(GitStageTrackerListener::class.java)
  private val singleTaskController = MySingleTaskController()

  var isRefreshInProgress = false
    private set

  @Volatile
  var state: State = State.EMPTY

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        scheduleUpdateForEvents(events)
      }
    })
    connection.subscribe(VcsDirtyScopeManagerListener.VCS_DIRTY_SCOPE_UPDATED, object : VcsDirtyScopeManagerListener {
      override fun everythingDirty() {
        scheduleUpdateAll()
      }

      override fun filePathsDirty(filesConverted: Map<VcsRoot, Set<FilePath>>, dirsConverted: Map<VcsRoot, Set<FilePath>>) {
        val files = filesConverted.filterKeys { it.vcs?.keyInstanceMethod == GitVcs.getKey() }.mapKeys { it.key.path }
        val dirs = dirsConverted.filterKeys { it.vcs?.keyInstanceMethod == GitVcs.getKey() }.mapKeys { it.key.path }
        singleTaskController.sendRequests(refreshFiles(files, dirs))
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt(this) {
        val roots = gitRoots()
        update { oldState -> State(oldState.rootStates.filterKeys { roots.contains(it) }) }
      }
    })
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
      override fun unsavedDocumentDropped(document: Document) {
        scheduleUpdateDocument(document)
      }

      override fun unsavedDocumentsDropped() {
        scheduleUpdateAll()
      }
    })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scheduleUpdateDocument(event.document)
      }
    }, this)
  }

  fun scheduleUpdateAll() {
    singleTaskController.sendRequests(refreshRoots(gitRoots()))
  }

  private fun scheduleUpdateDocument(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    val root = getRoot(project, file) ?: return
    if (!gitRoots().contains(root)) return
    singleTaskController.request(Request.RefreshFiles(mutableMapOf(Pair(root, setOf(file.filePath()))), emptyMap()))
  }

  private fun scheduleUpdateForEvents(events: List<VFileEvent>) {
    val gitRoots = gitRoots()
    val roots = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
      events.any { e -> GitUntrackedFilesHolder.totalRefreshNeeded(repo, e.path) }
    }.map { it.root }.intersect(gitRoots)
    val files = events.mapNotNull { it.file as? GitIndexVirtualFile }.map { Pair(it.root, it.filePath) }.filter {
      gitRoots.contains(it.first)
    }.toMapOfSets()
    singleTaskController.sendRequests(refreshRoots(roots), refreshFiles(files))
  }

  fun addListener(listener: GitStageTrackerListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  private fun gitRoots(): List<VirtualFile> {
    return ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toList()
  }

  private fun update(updater: (State) -> State) {
    this.state = updater(this.state)
    eventDispatcher.multicaster.update()
  }

  private fun update(stateAndScopes: Pair<State, Map<VirtualFile, DirtyScope>>) {
    update { oldState ->
      oldState.updatedWith(stateAndScopes.first, stateAndScopes.second)
    }
  }

  private fun refreshRoots(roots: Collection<VirtualFile>): Request.RefreshRoots? {
    if (roots.isEmpty()) return null
    return Request.RefreshRoots(roots)
  }

  private fun refreshFiles(files: Map<VirtualFile, Set<FilePath>>,
                           dirs: Map<VirtualFile, Set<FilePath>> = emptyMap()): Request.RefreshFiles? {
    if (files.values.isEmpty() && dirs.values.isEmpty()) return null
    return Request.RefreshFiles(files, dirs)
  }

  override fun dispose() {
    state = State.EMPTY
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(GitStageTracker::class.java)
  }

  private sealed class Request {
    class RefreshRoots(val roots: Collection<VirtualFile>) : Request() {
      override fun toString(): String = "Request.RefreshRoots(roots=${roots.joinToString(",") { it.name }})"
    }

    class RefreshFiles(val files: Map<VirtualFile, Set<FilePath>>, val dirs: Map<VirtualFile, Set<FilePath>>) : Request() {
      override fun toString(): String = "Request.RefreshFiles(files=${files.toShortenedString(",\n")},\n" +
                                        " dirs=${dirs.toShortenedString(",\n")})"
    }
  }

  data class RootState(val root: VirtualFile,
                       val statuses: Map<FilePath, GitFileStatus>,
                       val unsavedIndex: List<GitIndexVirtualFile>,
                       val unsavedWorkTree: List<VirtualFile>) {
    fun hasStagedFiles(): Boolean {
      return statuses.values.any { line -> line.getStagedStatus() != null } || unsavedIndex.isNotEmpty()
    }

    internal fun updateWith(newState: RootState, scope: DirtyScope): RootState {
      val newStatuses = mutableMapOf<FilePath, GitFileStatus>()
      newStatuses.putAll(newState.statuses)
      for ((file, record) in statuses) {
        if (!scope.belongsTo(root, file)) {
          newStatuses.putIfAbsent(file, record)
        }
      }

      val newUnsavedIndex = mutableListOf<GitIndexVirtualFile>()
      newUnsavedIndex.addAll(newState.unsavedIndex)
      newUnsavedIndex.addAll(unsavedIndex.filter { file -> !scope.belongsTo(root, file.filePath) })

      val newUnsavedWorkTree = mutableListOf<VirtualFile>()
      newUnsavedWorkTree.addAll(newState.unsavedWorkTree)
      newUnsavedWorkTree.addAll(newUnsavedWorkTree.filter { file -> !scope.belongsTo(root, file.filePath()) })
      return RootState(root, newStatuses, newUnsavedIndex, newUnsavedWorkTree)
    }

    fun isEmpty(): Boolean {
      return statuses.isEmpty() && unsavedIndex.isEmpty() && unsavedWorkTree.isEmpty()
    }

    override fun toString(): String {
      return "RootState(root=${root.name}, statuses=${statuses.toShortenedString(",\n")},\n " +
             "unsavedIndex=${unsavedIndex.toShortenedString(",\n")},\n " +
             "unsavedWorkTree=${unsavedWorkTree.toShortenedString(",\n")})"
    }

    companion object {
      fun empty(root: VirtualFile) = RootState(root, emptyMap(), emptyList(), emptyList())
    }
  }

  data class State(val rootStates: Map<VirtualFile, RootState>) {
    val stagedRoots: Set<VirtualFile>
      get() = rootStates.filterValues(RootState::hasStagedFiles).keys

    fun hasStagedRoots(): Boolean = rootStates.any { it.value.hasStagedFiles() }

    internal fun updatedWith(newState: State, scopes: Map<VirtualFile, DirtyScope>): State {
      val result = mutableMapOf<VirtualFile, RootState>()
      result.putAll(rootStates)
      for ((root, scope) in scopes) {
        if (scope is DirtyScope.Root) {
          result[root] = newState.rootStates.getValue(root)
        }
        else {
          val newRootState = newState.rootStates[root] ?: RootState.empty(root)
          result[root] = rootStates[root]?.updateWith(newRootState, scope) ?: newRootState
        }
      }
      return State(result)
    }

    override fun toString(): String {
      return "State(${rootStates.toShortenedString(separator = "\n") { "${it.key.name}=${it.value}" }}"
    }

    companion object {
      internal val EMPTY = State(emptyMap())
    }
  }

  private inner class MySingleTaskController :
    BaseSingleTaskController<Request, Pair<State, Map<VirtualFile, DirtyScope>>>("stage", this::update, this) {
    override fun process(requests: List<Request>,
                         previousState: Pair<State, Map<VirtualFile, DirtyScope>>?): Pair<State, Map<VirtualFile, DirtyScope>> {
      val rootRequests = requests.filterIsInstance<Request.RefreshRoots>()
      val filesRequests = requests.filterIsInstance<Request.RefreshFiles>()

      val scopes = mutableMapOf<VirtualFile, DirtyScope>()
      scopes.putAll(rootRequests.flatMapTo(HashSet()) { it.roots }.associateWith { DirtyScope.Root(it) }.toMap())

      for (request in filesRequests) {
        for (root in request.files.keys.union(request.dirs.keys).toHashSet()) {
          val scope = scopes.getOrPut(root) { DirtyScope.Paths(root) }
          if (scope is DirtyScope.Root) continue
          request.files[root]?.let { files -> scope.addDirtyPaths(files, false) }
          request.dirs[root]?.let { files -> scope.addDirtyPaths(files, true) }
        }
      }
      scopes.values.forEach { it.pack() }

      val unsaved = mutableMapOf<VirtualFile, MutableCollection<VirtualFile>>()
      for (document in FileDocumentManager.getInstance().unsavedDocuments) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: continue
        if (!file.isValid || !FileDocumentManager.getInstance().isFileModified(file)) continue
        val root = getRoot(project, file) ?: continue
        if (scopes[root]?.belongsTo(root, file.filePath()) == true) {
          unsaved.getOrPut(root) { mutableSetOf() }.add(file)
        }
      }

      val newRootState = mutableMapOf<VirtualFile, RootState>()
      for ((root, scope) in scopes) {
        val status = getStatus(project, root, scope.dirtyPaths()).map { GitFileStatus(root, it) }.associateBy { it.path }
        val (index, workTree) = getUnsaved(unsaved.getOrDefault(root, mutableSetOf()), status)
        newRootState[root] = RootState(root, status, index, workTree)
      }

      val newState = State(newRootState)
      return Pair(previousState?.first?.updatedWith(newState, scopes) ?: newState,
                  mergeScopes(previousState?.second, scopes))
    }

    private fun getUnsaved(unsaved: Collection<VirtualFile>,
                           status: Map<FilePath, GitFileStatus>): Pair<List<GitIndexVirtualFile>, List<VirtualFile>> {
      val index = mutableListOf<GitIndexVirtualFile>()
      val workTree = mutableListOf<VirtualFile>()
      for (file in unsaved) {
        val fileStatus: GitFileStatus? = status[file.filePath()]
        if (fileStatus?.isTracked() == false) continue
        if (file is GitIndexVirtualFile && fileStatus?.getStagedStatus() == null) {
          index.add(file)
        }
        else if (file.isInLocalFileSystem && fileStatus?.getUnStagedStatus() == null) {
          workTree.add(file)
        }
      }
      return Pair(index, workTree)
    }

    private fun mergeScopes(previousScopes: Map<VirtualFile, DirtyScope>?,
                            newScopes: Map<VirtualFile, DirtyScope>): Map<VirtualFile, DirtyScope> {
      if (previousScopes == null) return newScopes

      val result = mutableMapOf<VirtualFile, DirtyScope>()
      result.putAll(previousScopes)
      for ((root, newScope) in newScopes) {
        val oldScope = result[root]
        if (oldScope is DirtyScope.Root) continue
        if (oldScope == null || newScope is DirtyScope.Root) {
          result[root] = newScope
          continue
        }
        if (newScope is DirtyScope.Paths) newScope.addTo(oldScope)
      }
      return result
    }

    override fun createProgressIndicator(): ProgressIndicator = MyProgressIndicator()
  }

  private inner class MyProgressIndicator : ProgressIndicatorBase() {
    override fun start() {
      super.start()
      runInEdt(this@GitStageTracker) {
        isRefreshInProgress = true
        eventDispatcher.multicaster.progressStarted()
      }
    }

    override fun stop() {
      super.stop()
      runInEdt(this@GitStageTracker) {
        eventDispatcher.multicaster.progressStopped()
        isRefreshInProgress = false
      }
    }
  }
}

interface GitStageTrackerListener : EventListener {
  fun update()
  fun progressStarted() = Unit
  fun progressStopped() = Unit
}

private fun getRoot(project: Project, file: VirtualFile): VirtualFile? {
  return when {
    file is GitIndexVirtualFile -> file.root
    file.isInLocalFileSystem -> ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file)
    else -> null
  }
}

fun GitStageTracker.status(file: VirtualFile): GitFileStatus? {
  val root = getRoot(project, file) ?: return null
  val rootState = state.rootStates[root] ?: return null
  return rootState.statuses[file.filePath()]
}