// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return TEMPLATES_LOCK.read { if (repository != null) commitTemplates.containsKey(repository) else commitTemplates.values.isNotEmpty() }
  }

  @JvmOverloads
  fun getTemplateContent(repository: GitRepository? = null): String? {
    return TEMPLATES_LOCK.read { if (repository != null) commitTemplates[repository]?.content else commitTemplates.values.firstOrNull()?.content }
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
      val eventPath = event.path

      for ((repository, template) in allTemplates) {
        ProgressManager.checkCanceled()
        if (eventPath != template.watchedRoot.rootPath) continue

        if (event is VFileDeleteEvent) {
          stopTrackCommitTemplate(repository)
        }
        else if (event is VFileContentChangeEvent || event is VFileMoveEvent || event is VFileCopyEvent) {
          reloadCommitTemplateContent(repository)
        }
        BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).notifyCommitTemplateChanged(repository)
      }
    }
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
      return FileUtil.loadFile(File(commitTemplateFilePath), GitConfigUtil.getCommitEncoding(project, repository.root))
    }
    catch (e: IOException) {
      LOG.warn("Cannot load commit template for repository $repository by path $commitTemplateFilePath", e)
      return null
    }
  }

  private fun resolveCommitTemplatePath(repository: GitRepository): String? {
    val gitCommitTemplatePath = Git.getInstance().config(repository, COMMIT_TEMPLATE).outputAsJoinedString
    return if (gitCommitTemplatePath.isNotBlank() && FileUtil.exists(gitCommitTemplatePath)) gitCommitTemplatePath else null
  }

  private fun trackCommitTemplate(repository: GitRepository) {
    val currentTemplatePath = resolveCommitTemplatePath(repository)
    if (currentTemplatePath == null) {
      stopTrackCommitTemplate(repository)
      return
    }
    val watchedTemplatePath = TEMPLATES_LOCK.read { commitTemplates[repository]?.watchedRoot }
    val lfs = LocalFileSystem.getInstance()
    when {
      watchedTemplatePath == null -> lfs.addRootToWatch(currentTemplatePath, false)
      watchedTemplatePath.rootPath != currentTemplatePath -> lfs.replaceWatchedRoot(watchedTemplatePath, currentTemplatePath, false)
      else -> null
    }?.also {
      //explicit refresh needed for global templates to subscribe them in VFS and receive VFS events
      lfs.refreshAndFindFileByPath(it.rootPath)
      val templateContent = loadTemplateContent(repository, it.rootPath)
      if (templateContent != null) {
        TEMPLATES_LOCK.write { commitTemplates[repository] = GitCommitTemplate(it, templateContent) }
        BackgroundTaskUtil.syncPublisher(project, GitCommitTemplateListener.TOPIC).notifyCommitTemplateChanged(repository)
      }
    }
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

  @JvmDefault
  fun loadingStarted() {}
  @JvmDefault
  fun loadingFinished() {}

  fun notifyCommitTemplateChanged(repository: GitRepository)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<GitCommitTemplateListener> =
      Topic(GitCommitTemplateListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
