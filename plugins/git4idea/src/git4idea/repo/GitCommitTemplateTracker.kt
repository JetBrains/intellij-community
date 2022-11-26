// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.ObjectUtils
import com.intellij.util.SystemProperties
import com.intellij.util.messages.Topic
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.config.GitConfigUtil
import git4idea.config.GitConfigUtil.COMMIT_TEMPLATE
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val LOG = logger<GitCommitTemplateTracker>()

@Service
internal class GitCommitTemplateTracker(private val project: Project) : GitConfigListener, AsyncVfsEventsListener, Disposable {
  private val commitTemplates = mutableMapOf<GitRepository, GitCommitTemplate>()
  private val TEMPLATES_LOCK = ReentrantReadWriteLock()

  private val started = AtomicBoolean()

  init {
    project.messageBus.connect(this).subscribe(GitConfigListener.TOPIC, this)
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, this)
  }

  fun isStarted() = started.get()

  fun templatesCount(): Int {
    return TEMPLATES_LOCK.read { commitTemplates.values.size }
  }

  @JvmOverloads
  fun exists(repository: GitRepository? = null): Boolean {
    return TEMPLATES_LOCK.read {
      if (repository != null) {
        commitTemplates[repository]?.content?.isNotBlank() == true
      }
      else {
        commitTemplates.values.any { it.content.isNotBlank() }
      }
    }
  }

  @JvmOverloads
  fun getTemplateContent(repository: GitRepository? = null): String? {
    return TEMPLATES_LOCK.read {
      if (repository != null) {
        commitTemplates[repository]?.content?.ifBlank { null }
      }
      else {
        commitTemplates.values.firstOrNull()?.content?.ifBlank { null }
      }
    }
  }

  override fun notifyConfigChanged(repository: GitRepository) {
    trackCommitTemplate(repository)
  }

  override fun filesChanged(events: List<VFileEvent>) {
    if (TEMPLATES_LOCK.read { commitTemplates.isEmpty() }) return

    BackgroundTaskUtil.runUnderDisposeAwareIndicator(this) { processEvents(events) }
  }

  private fun start() {
    BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).loadingStarted()
    GitUtil.getRepositories(project).forEach(::trackCommitTemplate)
    if (started.compareAndSet(false, true)) {
      BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).loadingFinished()
    }
  }

  private fun processEvents(events: List<VFileEvent>) {
    val allTemplates = TEMPLATES_LOCK.read { commitTemplates.toMap() }
    if (allTemplates.isEmpty()) return

    for (event in events) {
      ProgressManager.checkCanceled()

      for ((repository, template) in allTemplates) {
        ProgressManager.checkCanceled()
        val watchedTemplatePath = template.watchedRoot.rootPath

        var templateChanged = false
          if (isEventToStopTracking(event, watchedTemplatePath)) {
            synchronized(this) {
              stopTrackCommitTemplate(repository)
            }
            templateChanged = true
          }
          else if (isEventToReloadTemplateContent(event, watchedTemplatePath)) {
            synchronized(this) {
              reloadCommitTemplateContent(repository)
            }
            templateChanged = true
          }

        if (templateChanged) {
          BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).notifyCommitTemplateChanged(repository)
        }
      }
    }
  }

  private fun isEventToStopTracking(event: VFileEvent, watchedTemplatePath: String): Boolean {
    return when {
      event is VFileDeleteEvent -> event.path == watchedTemplatePath
      event is VFileMoveEvent -> event.oldPath == watchedTemplatePath
      event is VFilePropertyChangeEvent && event.isRename -> event.oldPath == watchedTemplatePath
      else -> false
    }
  }

  private fun isEventToReloadTemplateContent(event: VFileEvent, watchedTemplatePath: String): Boolean {
    return event is VFileContentChangeEvent && event.path == watchedTemplatePath
  }

  private fun stopTrackCommitTemplate(repository: GitRepository) {
    val commitTemplate = TEMPLATES_LOCK.write { commitTemplates.remove(repository) } ?: return
    LocalFileSystem.getInstance().removeWatchedRoot(commitTemplate.watchedRoot)
  }

  private fun reloadCommitTemplateContent(repository: GitRepository) {
    val commitTemplateRootPath = TEMPLATES_LOCK.read { commitTemplates[repository] }?.watchedRoot?.rootPath ?: return
    val loadedContent = loadTemplateContent(repository, commitTemplateRootPath) ?: return
    TEMPLATES_LOCK.write {
      commitTemplates[repository]?.let { commitTemplate -> commitTemplate.content = loadedContent }
    }
  }

  private fun loadTemplateContent(repository: GitRepository, commitTemplateFilePath: String): String? {
    try {
      val fileContent = FileUtil.loadFile(File(commitTemplateFilePath), GitConfigUtil.getCommitEncoding(project, repository.root))
      if (fileContent.isBlank()) {
        LOG.warn("Empty or blank commit template detected for repository $repository by path $commitTemplateFilePath")
      }
      return fileContent
    }
    catch (e: IOException) {
      LOG.warn("Cannot load commit template for repository $repository by path $commitTemplateFilePath", e)
      return null
    }
  }

  private fun resolveCommitTemplatePath(repository: GitRepository): String? {
    val gitCommitTemplatePath = Git.getInstance().config(repository, COMMIT_TEMPLATE).outputAsJoinedString
    if (gitCommitTemplatePath.isBlank()) return null

    return if (FileUtil.exists(gitCommitTemplatePath)) {
      gitCommitTemplatePath
    }
    else
      ObjectUtils.chooseNotNull(getPathRelativeToUserHome(gitCommitTemplatePath),
                                repository.findPathRelativeToRootDirs(gitCommitTemplatePath))
  }

  private fun getPathRelativeToUserHome(fileNameOrPath: String): String? {
    if (fileNameOrPath.startsWith('~')) {
      val fileAtUserHome = File(SystemProperties.getUserHome(), fileNameOrPath.substring(1))
      if (fileAtUserHome.exists()) {
        return fileAtUserHome.path
      }
    }

    return null
  }

  private fun GitRepository.findPathRelativeToRootDirs(relativeFilePath: String): String? {
    if (relativeFilePath.startsWith('/') || relativeFilePath.endsWith('/')) return null

    for (rootDir in repositoryFiles.rootDirs) {
      val rootDirParent = rootDir.parent?.path ?: continue
      val templateFile = File(rootDirParent, relativeFilePath)
      if (templateFile.exists()) return templateFile.path
    }

    return null
  }

  private fun trackCommitTemplate(repository: GitRepository) {
    val newTemplatePath = resolveCommitTemplatePath(repository)
    val templateChanged = synchronized(this) {
      updateTemplatePath(repository, newTemplatePath)
    }

    if (templateChanged) {
      BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).notifyCommitTemplateChanged(repository)
    }
  }

  private fun updateTemplatePath(repository: GitRepository, newTemplatePath: String?): Boolean {
    val oldWatchRoot = TEMPLATES_LOCK.read { commitTemplates[repository]?.watchedRoot }
    val oldTemplatePath = oldWatchRoot?.rootPath
    if (oldTemplatePath == newTemplatePath) return false
    if (newTemplatePath == null) {
      stopTrackCommitTemplate(repository)
      return true
    }

    val lfs = LocalFileSystem.getInstance()
    //explicit refresh needed for global templates to subscribe them in VFS and receive VFS events
    lfs.refreshAndFindFileByPath(newTemplatePath)

    val templateContent = loadTemplateContent(repository, newTemplatePath)
    if (templateContent == null) {
      stopTrackCommitTemplate(repository)
      return true
    }

    val newWatchRoot = when {
      oldWatchRoot != null -> lfs.replaceWatchedRoot(oldWatchRoot, newTemplatePath, false)
      else -> lfs.addRootToWatch(newTemplatePath, false)
    }

    if (newWatchRoot == null) {
      LOG.error("Cannot add root to watch $newTemplatePath")
      if (oldWatchRoot != null) {
        stopTrackCommitTemplate(repository)
        return true
      }
      return false
    }

    TEMPLATES_LOCK.write { commitTemplates[repository] = GitCommitTemplate(newWatchRoot, templateContent) }
    return true
  }

  override fun dispose() {
    val watchRootsToDispose = TEMPLATES_LOCK.read { commitTemplates.values.map(GitCommitTemplate::watchedRoot) }
    if (watchRootsToDispose.isEmpty()) return

    val lfs = LocalFileSystem.getInstance()
    for (watchedRoot in watchRootsToDispose) {
      lfs.removeWatchedRoot(watchedRoot)
    }

    TEMPLATES_LOCK.write {
      commitTemplates.clear()
    }
  }

  internal class GitCommitTemplateTrackerStartupActivity : VcsStartupActivity {

    override fun runActivity(project: Project) {
      project.service<GitCommitTemplateTracker>().start()
    }

    override fun getOrder(): Int = VcsInitObject.AFTER_COMMON.order
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitCommitTemplateTracker = project.service()
  }
}

private class GitCommitTemplate(val watchedRoot: LocalFileSystem.WatchRequest,
                                var content: String)

internal interface GitCommitTemplateListener {

  fun loadingStarted() {}
  fun loadingFinished() {}

  fun notifyCommitTemplateChanged(repository: GitRepository)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<GitCommitTemplateListener> =
      Topic(GitCommitTemplateListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
