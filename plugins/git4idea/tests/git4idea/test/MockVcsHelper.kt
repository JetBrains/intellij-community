/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.test

import com.intellij.ide.errorTreeView.HotfixData
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import java.io.File

class MockVcsHelper(project: Project) : AbstractVcsHelper(project) {
  @Volatile private var myCommitDialogShown: Boolean = false
  @Volatile private var myMergeDialogShown: Boolean = false
  @Volatile private var myMergeDelegate: () -> Unit = { throw IllegalStateException() }
  @Volatile private var myCommitDelegate: (String) -> Boolean = { throw IllegalStateException() }

  override fun runTransactionRunnable(vcs: AbstractVcs<*>?, runnable: TransactionRunnable?, vcsParameters: Any?): MutableList<VcsException>? {
    throw UnsupportedOperationException()
  }

  override fun showAnnotation(annotation: FileAnnotation?, file: VirtualFile?, vcs: AbstractVcs<*>?) {
    throw UnsupportedOperationException()
  }

  override fun showAnnotation(annotation: FileAnnotation?, file: VirtualFile?, vcs: AbstractVcs<*>?, line: Int) {
    throw UnsupportedOperationException()
  }

  override fun showChangesBrowser(provider: CommittedChangesProvider<*, *>?, location: RepositoryLocation?, title: String?, parent: Component?) {
    throw UnsupportedOperationException()
  }

  override fun openCommittedChangesTab(vcs: AbstractVcs<*>?, root: VirtualFile?, settings: ChangeBrowserSettings?, maxCount: Int, title: String?) {
    throw UnsupportedOperationException()
  }

  override fun openCommittedChangesTab(provider: CommittedChangesProvider<*, *>?, location: RepositoryLocation?, settings: ChangeBrowserSettings?, maxCount: Int, title: String?) {
    throw UnsupportedOperationException()
  }

  override fun showFileHistory(historyProvider: VcsHistoryProvider, path: FilePath, vcs: AbstractVcs<*>, repositoryPath: String?) {
    throw UnsupportedOperationException()
  }

  override fun showFileHistory(historyProvider: VcsHistoryProvider, annotationProvider: AnnotationProvider?, path: FilePath, repositoryPath: String?, vcs: AbstractVcs<*>) {
    throw UnsupportedOperationException()
  }

  override fun showErrors(abstractVcsExceptions: List<VcsException>, tabDisplayName: String) {
    throw UnsupportedOperationException()
  }

  override fun showErrors(exceptionGroups: Map<HotfixData, List<VcsException>>, tabDisplayName: String) {
    throw UnsupportedOperationException()
  }

  override fun showDifferences(cvsVersionOn: VcsFileRevision, cvsVersionOn1: VcsFileRevision, file: File) {
    throw UnsupportedOperationException()
  }

  override fun showChangesListBrowser(changelist: CommittedChangeList, title: String) {
    throw UnsupportedOperationException()
  }

  override fun showChangesBrowser(changelists: List<CommittedChangeList>) {
    throw UnsupportedOperationException()
  }

  override fun showChangesBrowser(changelists: List<CommittedChangeList>, title: String) {
    throw UnsupportedOperationException()
  }

  override fun showWhatDiffersBrowser(parent: Component?, changes: Collection<Change>, title: String) {
    throw UnsupportedOperationException()
  }

  override fun <T : CommittedChangeList, U : ChangeBrowserSettings> chooseCommittedChangeList(provider: CommittedChangesProvider<T, U>, location: RepositoryLocation): T? {
    throw UnsupportedOperationException()
  }

  override fun showRollbackChangesDialog(changes: List<Change>) {
    throw UnsupportedOperationException()
  }

  override fun selectFilesToProcess(files: List<VirtualFile>, title: String, prompt: String?, singleFileTitle: String, singleFilePromptTemplate: String, confirmationOption: VcsShowConfirmationOption): Collection<VirtualFile>? {
    throw UnsupportedOperationException()
  }

  override fun selectFilePathsToProcess(files: List<FilePath>, title: String, prompt: String?, singleFileTitle: String, singleFilePromptTemplate: String, confirmationOption: VcsShowConfirmationOption): Collection<FilePath>? {
    throw UnsupportedOperationException()
  }

  override fun loadAndShowCommittedChangesDetails(project: Project, revision: VcsRevisionNumber, file: VirtualFile, key: VcsKey, location: RepositoryLocation?, local: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun showMergeDialog(files: List<VirtualFile>, provider: MergeProvider, mergeDialogCustomizer: MergeDialogCustomizer): List<VirtualFile> {
    myMergeDialogShown = true
    myMergeDelegate()
    return emptyList()
  }

  override fun commitChanges(changes: Collection<Change>, initialChangeList: LocalChangeList, commitMessage: String, customResultHandler: CommitResultHandler?): Boolean {
    myCommitDialogShown = true

    val success = myCommitDelegate(commitMessage)
    if (customResultHandler != null) {
      if (success) {
        customResultHandler.onSuccess(commitMessage)
      }
      else {
        customResultHandler.onFailure()
      }
    }
    return success
  }

  fun mergeDialogWasShown(): Boolean {
    return myMergeDialogShown
  }

  fun commitDialogWasShown(): Boolean {
    return myCommitDialogShown
  }

  fun onMerge(delegate : () -> Unit) {
    myMergeDelegate = delegate
  }

  fun onCommit(delegate : (String) -> Boolean) {
    myCommitDelegate = delegate
  }

  @Deprecated("use onCommit") fun registerHandler(handler: CommitHandler) {
    myCommitDelegate = { handler.commit(it) }
  }

  @Deprecated("use onMerge") fun registerHandler(handler: MergeHandler) {
    myMergeDelegate = { handler.showMergeDialog() }
  }

  @Deprecated("use onCommit") interface CommitHandler {
    fun commit(commitMessage: String): Boolean
  }

  @Deprecated("use onMerge") interface MergeHandler {
    fun showMergeDialog()
  }

}
