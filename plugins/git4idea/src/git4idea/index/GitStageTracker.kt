// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.AppTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
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
import git4idea.repo.GitUntrackedFilesHolder
import git4idea.status.GitChangeProvider
import git4idea.util.toShortenedString
import java.util.*

private val PROCESSED = Key.create<Boolean>("GitStageTracker.file.processed")

class GitStageTracker(val project: Project) : Disposable {
  private val eventDispatcher = EventDispatcher.create(GitStageTrackerListener::class.java)
  private val dirtyScopeManager
    get() = VcsDirtyScopeManager.getInstance(project)

  @Volatile
  var state: State = State.EMPTY

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        markDirty(events)
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
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        file.putUserData(PROCESSED, null)
        markDirty(file)
      }

      override fun fileContentReloaded(file: VirtualFile, document: Document) {
        file.putUserData(PROCESSED, null)
        markDirty(file)
      }

      override fun fileWithNoDocumentChanged(file: VirtualFile) {
        file.putUserData(PROCESSED, null)
        markDirty(file)
      }

      override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        file.putUserData(PROCESSED, null)
      }
    })
    connection.subscribe(GitChangeProvider.TOPIC, GitChangeProvider.ChangeProviderListener { repository ->
      doUpdateState(repository)
    })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (file.getUserData(PROCESSED) == null) {
          file.putUserData(PROCESSED, true)
          markDirty(file)
        }
      }
    }, this)
  }

  fun scheduleUpdateAll() {
    val gitRoots = gitRoots()
    LOG.debug("Mark dirty ${gitRoots}")
    dirtyScopeManager.filesDirty(emptyList(), gitRoots)
  }

  private fun markDirty(file: VirtualFile) {
    val root = getRoot(project, file) ?: return
    if (!gitRoots().contains(root)) return
    LOG.debug("Mark dirty ${file.filePath()}")
    dirtyScopeManager.fileDirty(file.filePath())
  }

  private fun markDirty(events: List<VFileEvent>) {
    val gitRoots = gitRoots()

    val roots = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
      events.any { e -> GitUntrackedFilesHolder.totalRefreshNeeded(repo, e.path) }
    }.map { it.root }.intersect(gitRoots)

    val files = events.mapNotNull { it.file as? GitIndexVirtualFile }.filter {
      gitRoots.contains(it.root)
    }.map { it.filePath }

    LOG.debug("Mark dirty", files, roots)
    dirtyScopeManager.filesDirty(emptyList(), roots)
    dirtyScopeManager.filePathsDirty(files, emptyList())
  }

  private fun doUpdateState(repository: GitRepository) {
    LOG.debug("Updating ${repository.root}")

    val untracked = repository.untrackedFilesHolder.untrackedFilePaths.map { GitFileStatus('?', '?', it, null) }
    val status = repository.stagingAreaHolder.allRecords.union(untracked).associateBy { it.path }

    val unsavedIndex = mutableListOf<GitIndexVirtualFile>()
    val unsavedWorkTree = mutableListOf<VirtualFile>()
    for (document in FileDocumentManager.getInstance().unsavedDocuments) {
      val file = FileDocumentManager.getInstance().getFile(document) ?: continue
      if (!file.isValid || !FileDocumentManager.getInstance().isFileModified(file)) continue
      val root = getRoot(project, file) ?: continue
      if (root != repository.root) continue

      if (repository.ignoredFilesHolder.containsFile(file.filePath())) continue
      val fileStatus: GitFileStatus? = status[file.filePath()]
      if (fileStatus?.isTracked() == false) continue

      if (file is GitIndexVirtualFile && fileStatus?.getStagedStatus() == null) {
        unsavedIndex.add(file)
      }
      else if (file.isInLocalFileSystem && fileStatus?.getUnStagedStatus() == null) {
        unsavedWorkTree.add(file)
      }
    }

    val newRootState = RootState(repository.root, status, unsavedIndex, unsavedWorkTree)

    runInEdt(this) {
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

  override fun dispose() {
    state = State.EMPTY
  }

  companion object {
    private val LOG = Logger.getInstance(GitStageTracker::class.java)

    @JvmStatic
    fun getInstance(project: Project) = project.getService(GitStageTracker::class.java)
  }

  data class RootState(val root: VirtualFile,
                       val statuses: Map<FilePath, GitFileStatus>,
                       val unsavedIndex: List<GitIndexVirtualFile>,
                       val unsavedWorkTree: List<VirtualFile>) {
    fun hasStagedFiles(): Boolean {
      return statuses.values.any { line -> line.getStagedStatus() != null } || unsavedIndex.isNotEmpty()
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

    internal fun updatedWith(root: VirtualFile, newState: RootState): State {
      val result = mutableMapOf<VirtualFile, RootState>()
      result.putAll(rootStates)
      result[root] = newState
      return State(result)
    }

    override fun toString(): String {
      return "State(${rootStates.toShortenedString(separator = "\n") { "${it.key.name}=${it.value}" }}"
    }

    companion object {
      internal val EMPTY = State(emptyMap())
    }
  }
}

interface GitStageTrackerListener : EventListener {
  fun update()
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