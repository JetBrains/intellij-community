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
        val roots = filesConverted.keys.union(dirsConverted.keys).filter { it.vcs?.keyInstanceMethod == GitVcs.getKey() }.map { it.path }
        singleTaskController.sendRequests(gitStateRequest(roots), unsavedFilesRequest(roots))
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      val roots = gitRoots()
      if (!singleTaskController.sendRequests(gitStateRequest(roots), unsavedFilesRequest(roots))) {
        runInEdt(this) {
          update(State.EMPTY)
        }
      }
    })
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
      override fun unsavedDocumentDropped(document: Document) {
        scheduleUpdateUnsaved(document)
      }

      override fun unsavedDocumentsDropped() {
        scheduleUpdateUnsaved()
      }
    })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scheduleUpdateUnsaved(event.document)
      }
    }, this)
  }

  fun scheduleUpdateAll() {
    singleTaskController.sendRequests(gitStateRequest(gitRoots()), unsavedFilesRequest(gitRoots()))
  }

  private fun scheduleUpdateUnsaved() {
    singleTaskController.sendRequests(unsavedFilesRequest(gitRoots()))
  }

  private fun scheduleUpdateUnsaved(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    getRoot(file)?.let { singleTaskController.request(Request.Unsaved(listOf(it))) }
  }

  private fun scheduleUpdateForEvents(events: List<VFileEvent>) {
    val updateGitStateFor = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
      events.any { e -> GitUntrackedFilesHolder.totalRefreshNeeded(repo, e.path) }
    }.map { it.root }
    val updateAllFor = events.mapNotNull { it.file as? GitIndexVirtualFile }.mapTo(mutableSetOf()) { it.root }
    singleTaskController.sendRequests(gitStateRequest(updateGitStateFor.union(updateAllFor)), unsavedFilesRequest(updateAllFor))
  }

  private fun getRoot(file: VirtualFile): VirtualFile? {
    return when {
      file is GitIndexVirtualFile -> file.root
      file.isInLocalFileSystem -> ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file)
      else -> null
    }
  }

  fun addListener(listener: GitStageTrackerListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  private fun gitRoots(): List<VirtualFile> {
    return ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toList()
  }

  private fun update(newState: State) {
    this.state = this.state.updatedWith(newState, roots = gitRoots())
    eventDispatcher.multicaster.update()
  }

  private fun gitStateRequest(roots: Collection<VirtualFile>): Request.GitState? {
    if (roots.isEmpty()) return null
    return Request.GitState(roots)
  }

  private fun unsavedFilesRequest(roots: Collection<VirtualFile>): Request.Unsaved? {
    if (roots.isEmpty()) return null
    return Request.Unsaved(roots)
  }

  override fun dispose() {
    state = State.EMPTY
  }

  private sealed class Request(val roots: Collection<VirtualFile>) {
    class GitState(roots: Collection<VirtualFile>) : Request(roots) {
      override fun toString(): String = "Request.GitState(roots=${roots.joinToString(",") { it.name }})"
    }

    class Unsaved(roots: Collection<VirtualFile>) : Request(roots) {
      override fun toString(): String = "Request.Unsaved(roots=${roots.joinToString(",") { it.name }})"
    }
  }

  data class State(val gitState: Map<VirtualFile, List<GitFileStatus>>,
                   val unsavedIndex: Map<VirtualFile, List<GitIndexVirtualFile>>,
                   val unsavedWorkTree: Map<VirtualFile, List<VirtualFile>>) {
    private val roots: Collection<VirtualFile>
      get() = gitState.keys.union(unsavedIndex.keys).union(unsavedWorkTree.keys)
    val stagedRoots: Set<VirtualFile>
      get() {
        return gitState.filterValues {
          it.any { line -> line.getStagedStatus() != null }
        }.keys.union(unsavedIndex.filterValues { it.isNotEmpty() }.keys)
      }

    fun hasStagedRoots(): Boolean {
      return gitState.values.flatten().any { it.getStagedStatus() != null } || unsavedIndex.values.flatten().isNotEmpty()
    }

    fun updatedWith(newState: State, roots: Collection<VirtualFile> = this.roots.union(newState.roots)): State {
      val gitState = hashMapOf<VirtualFile, List<GitFileStatus>>()
      val unsavedIndex = hashMapOf<VirtualFile, List<GitIndexVirtualFile>>()
      val unsavedWorkTree = hashMapOf<VirtualFile, List<VirtualFile>>()
      for (root in roots) {
        gitState[root] = newState.gitState[root] ?: this.gitState[root] ?: emptyList()
        unsavedIndex[root] = newState.unsavedIndex[root] ?: this.unsavedIndex[root] ?: emptyList()
        unsavedWorkTree[root] = newState.unsavedWorkTree[root] ?: this.unsavedWorkTree[root] ?: emptyList()
      }
      return State(gitState, unsavedIndex, unsavedWorkTree)
    }

    override fun toString(): String {
      return "State(gitState=$gitState, unsavedIndex=$unsavedIndex, unsavedWorkTree=$unsavedWorkTree)"
    }

    companion object {
      internal val EMPTY = State(emptyMap(), emptyMap(), emptyMap())
    }
  }

  private inner class MySingleTaskController : BaseSingleTaskController<Request, State>("Git Stage Tracker",
                                                                                        this::update, this) {
    override fun process(requests: List<Request>, previousState: State?): State {
      val gitStateRoots = requests.filterIsInstance<Request.GitState>().flatMapTo(HashSet()) { it.roots }
      val gitState = mutableMapOf<VirtualFile, List<GitFileStatus>>()
      for (root in gitStateRoots) {
        gitState[root] = getStatus(project, root, withIgnored = false).map { GitFileStatus(root, it) }
      }

      val unsavedRoots = requests.filterIsInstance<Request.Unsaved>().flatMapTo(HashSet()) { it.roots }
      val index = unsavedRoots.associateWith { mutableListOf<GitIndexVirtualFile>() }
      val workTree = unsavedRoots.associateWith { mutableListOf<VirtualFile>() }
      for (document in FileDocumentManager.getInstance().unsavedDocuments) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: continue
        if (!file.isValid || !FileDocumentManager.getInstance().isFileModified(file)) continue
        val root = getRoot(file) ?: continue
        if (!unsavedRoots.contains(root)) continue

        val statusRecord: GitFileStatus? = if (gitStateRoots.contains(root)) {
          gitState[root]?.find { it.path == file.filePath() }
        }
        else if (previousState?.gitState?.containsKey(root) == true) {
          previousState.gitState[root]?.find { it.path == file.filePath() }
        }
        else {
          state.gitState[root]?.find { it.path == file.filePath() }
        }
        if (statusRecord?.isTracked() == false) continue
        if (file is GitIndexVirtualFile && statusRecord?.getStagedStatus() == null) {
          index.getValue(root).add(file)
        }
        else if (file.isInLocalFileSystem && statusRecord?.getUnStagedStatus() == null) {
          workTree.getValue(root).add(file)
        }
      }

      val newState = State(gitState, index, workTree)
      return previousState?.updatedWith(newState) ?: newState
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