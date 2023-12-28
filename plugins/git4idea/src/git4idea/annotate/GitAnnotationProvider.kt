// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.VcsCacheManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vcsUtil.VcsUtil.getLastCommitPath
import git4idea.GitRevisionNumber
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.CalledInAny
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@CalledInAny
internal fun getAnnotationFromCache(project: Project, file: VirtualFile): FileAnnotation? {
  val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file) ?: return null
  val currentRevision = repository.currentRevision ?: return null

  val cache = VcsCacheManager.getInstance(project).vcsHistoryCache
  val filePath = getLastCommitPath(project, VcsUtil.getFilePath(file))
  val lastRevision = cache.getLastRevision(filePath, GitVcs.getKey(), GitRevisionNumber(currentRevision)) ?: return null
  val annotationData = cache.getAnnotation(filePath, GitVcs.getKey(), lastRevision) as? GitAnnotationProvider.CachedData ?: return null

  return GitFileAnnotation(project, file, lastRevision, annotationData.lines)
}

fun reportAnnotationFinished(project: Project,
                             root: VirtualFile,
                             file: VirtualFile,
                             revision: VcsRevisionNumber?,
                             annotation: GitFileAnnotation,
                             startMs: Long,
                             provider: String) {
  val duration = (System.currentTimeMillis() - startMs).toDuration(DurationUnit.MILLISECONDS)

  GitAnnotationPerformanceListener.EP_NAME.extensionList.forEach {
    it.onAnnotationFinished(project, root, file, revision, annotation, duration, provider)
  }
}