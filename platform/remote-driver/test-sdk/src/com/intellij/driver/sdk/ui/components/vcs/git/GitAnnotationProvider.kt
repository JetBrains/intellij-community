package com.intellij.driver.sdk.ui.components.vcs.git

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.VirtualFile
import java.util.Date


@Remote("git4idea.annotate.GitAnnotationProvider", plugin = "Git4Idea/intellij.vcs.git.backend")
interface GitAnnotationProvider {
  fun annotate(file: VirtualFile): FileAnnotation
}

@Remote("com.intellij.openapi.vcs.annotate.FileAnnotation", plugin = "intellij.vcs.plugin/intellij.platform.vcs")
interface FileAnnotation {
  fun getRevisions(): List<VcsFileRevision>
}

@Remote("com.intellij.openapi.vcs.history.VcsFileRevision", plugin = "intellij.vcs.plugin/intellij.platform.vcs")
interface VcsFileRevision {
  fun getRevisionDate(): Date
  fun getAuthor(): String
  fun getCommitMessage(): String
  fun getBranchName(): String
}
