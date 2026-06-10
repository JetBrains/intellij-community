package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.VirtualFile
import java.util.Date


@Remote("git4idea.annotate.GitAnnotationProvider", plugin = "Git4Idea/intellij.vcs.git.backend")
interface GitAnnotationProvider {
  fun annotate(file: VirtualFile): FileAnnotation
}

@Remote("com.intellij.openapi.vcs.annotate.FileAnnotation")
interface FileAnnotation {
  fun getRevisions(): List<VcsFileRevision>
}

@Remote("com.intellij.openapi.vcs.history.VcsFileRevision")
interface VcsFileRevision {
  fun getRevisionDate(): Date
  fun getAuthor(): String
  fun getCommitMessage(): String
  fun getBranchName(): String
}
