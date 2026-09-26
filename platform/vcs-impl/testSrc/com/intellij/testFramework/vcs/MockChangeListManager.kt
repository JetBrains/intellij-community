// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListData
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.vcsUtil.VcsUtil
import java.io.File

open class MockChangeListManager : ChangeListManagerEx() {
  private val changeLists = HashMap<String, MockChangeList>()
  private var activeChangeList: LocalChangeList
  private val _defaultChangeList = MockChangeList(LocalChangeList.getDefaultName())

  init {
    changeLists[LocalChangeList.getDefaultName()] = _defaultChangeList
    activeChangeList = _defaultChangeList
  }

  fun addChanges(vararg changes: Change?) {
    val changeList = changeLists[LocalChangeList.getDefaultName()]!!
    for (change in changes) {
      changeList.add(change)
    }
  }

  override fun areChangeListsEnabled(): Boolean = true

  override fun getChangeListsNumber(): Int = changeLists.size

  override fun getChangeLists(): List<LocalChangeList?> {
    return ArrayList<LocalChangeList?>(changeLists.values)
  }

  override fun getAffectedPaths(): List<File?> {
    throw UnsupportedOperationException()
  }

  override fun getAffectedFiles(): List<VirtualFile?> {
    throw UnsupportedOperationException()
  }

  override fun isFileAffected(file: VirtualFile): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getAllChanges(): Collection<Change> {
    val changes = ArrayList<Change>()
    for (list in changeLists.values) {
      changes.addAll(list.changes)
    }
    return changes
  }

  override fun findChangeList(name: String?): LocalChangeList? {
    throw UnsupportedOperationException()
  }

  override fun getChangeList(id: String?): LocalChangeList? {
    throw UnsupportedOperationException()
  }

  override fun getDefaultChangeList(): LocalChangeList {
    return activeChangeList
  }

  override fun getChangeList(change: Change): LocalChangeList? {
    throw UnsupportedOperationException()
  }

  override fun getChangeLists(change: Change): List<LocalChangeList?> {
    throw UnsupportedOperationException()
  }

  override fun getChangeLists(file: VirtualFile): List<LocalChangeList?> {
    throw UnsupportedOperationException()
  }

  override fun getChangeListNameIfOnlyOne(changes: Array<Change?>?): String? {
    throw UnsupportedOperationException()
  }

  override fun scheduleAutomaticEmptyChangeListDeletion(list: LocalChangeList) {
    scheduleAutomaticEmptyChangeListDeletion(list, false)
  }

  override fun scheduleAutomaticEmptyChangeListDeletion(list: LocalChangeList, silently: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun getChange(file: VirtualFile): Change? {
    return getChange(VcsUtil.getFilePath(file))
  }

  override fun getChangeList(file: VirtualFile): LocalChangeList? {
    throw UnsupportedOperationException()
  }

  override fun getChange(file: FilePath?): Change? {
    for (change in getAllChanges()) {
      val before = change.beforeRevision
      val after = change.afterRevision
      if (after != null && after.getFile() == file || before != null && before.getFile() == file) {
        return change
      }
    }
    return null
  }

  override fun isUnversioned(file: VirtualFile): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getUnversionedFilesPaths(): List<FilePath?> {
    throw UnsupportedOperationException()
  }

  override fun isResolvedConflict(file: FilePath): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getResolvedConflictPaths(): List<FilePath?> {
    throw UnsupportedOperationException()
  }

  override fun getStatus(file: FilePath): FileStatus {
    throw UnsupportedOperationException()
  }

  override fun getStatus(file: VirtualFile): FileStatus {
    throw UnsupportedOperationException()
  }

  override fun getChangesIn(dir: VirtualFile): Collection<Change?> {
    return getChangesIn(VcsUtil.getFilePath(dir))
  }

  override fun getChangesIn(path: FilePath): Collection<Change?> {
    val changes = ArrayList<Change>()
    for (change in getAllChanges()) {
      val before = change.beforeRevision
      val after = change.afterRevision
      if (before != null && before.getFile().isUnder(path, false) || after != null && after.getFile().isUnder(path, false)) {
        changes.add(change)
      }
    }
    return changes
  }

  override fun haveChangesUnder(vf: VirtualFile): ThreeState {
    throw UnsupportedOperationException()
  }

  override fun addChangeListListener(listener: ChangeListListener, disposable: Disposable) {
    throw UnsupportedOperationException()
  }

  override fun addChangeListListener(listener: ChangeListListener) {
    throw UnsupportedOperationException()
  }

  override fun removeChangeListListener(listener: ChangeListListener) {
    throw UnsupportedOperationException()
  }

  override fun commitChanges(changeList: LocalChangeList, changes: List<Change?>) {
    throw UnsupportedOperationException()
  }

  @Deprecated("Deprecated in Java")
  @Suppress("removal")
  override fun reopenFiles(paths: List<FilePath>) {
    throw UnsupportedOperationException()
  }

  override fun addUnversionedFiles(list: LocalChangeList?, files: List<VirtualFile>) {
    throw UnsupportedOperationException()
  }

  override fun isIgnoredFile(file: VirtualFile): Boolean {
    throw UnsupportedOperationException()
  }

  override fun isIgnoredFile(file: FilePath): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getIgnoredFilePaths(): List<FilePath?> {
    throw UnsupportedOperationException()
  }

  override fun getSwitchedBranch(file: VirtualFile): String? {
    throw UnsupportedOperationException()
  }

  override fun getDefaultListName(): String {
    throw UnsupportedOperationException()
  }

  override fun freeze(reason: String) {
  }

  override fun unfreeze() {
  }

  override fun waitForUpdate() {
    throw UnsupportedOperationException()
  }

  override fun isFreezed(): String? {
    throw UnsupportedOperationException()
  }

  override fun isFreezedWithNotification(modalTitle: String?): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getModifiedWithoutEditing(): List<VirtualFile?> {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun addChangeList(name: String, comment: String?): LocalChangeList {
    val changeList = MockChangeList(name)
    changeLists[name] = changeList
    return changeList
  }

  override fun setDefaultChangeList(name: String) {
    throw UnsupportedOperationException()
  }

  override fun setDefaultChangeList(list: LocalChangeList) {
    activeChangeList = list
  }

  override fun setDefaultChangeList(list: LocalChangeList, automatic: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun removeChangeList(name: String) {
    throw UnsupportedOperationException()
  }

  override fun removeChangeList(list: LocalChangeList) {
    changeLists.remove(list.getName())
    if (activeChangeList == list) {
      activeChangeList = _defaultChangeList
    }
  }

  override fun moveChangesTo(list: LocalChangeList, vararg changes: Change?) {
  }

  override fun moveChangesTo(list: LocalChangeList, changes: List<Change>) {
  }

  override fun setReadOnly(name: String, value: Boolean): Boolean {
    throw UnsupportedOperationException()
  }

  override fun editName(fromName: String, toName: String): Boolean {
    throw UnsupportedOperationException()
  }

  override fun editComment(fromName: String, newComment: String?): String? {
    throw UnsupportedOperationException()
  }

  override fun editChangeListData(name: String, newData: ChangeListData?): Boolean {
    throw UnsupportedOperationException()
  }

  override fun isInUpdate(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getUpdateException(): VcsException? {
    return null
  }

  override fun getAffectedLists(changes: Collection<Change>): Collection<LocalChangeList> {
    throw UnsupportedOperationException()
  }

  override fun addChangeList(name: String, comment: String?, data: ChangeListData?): LocalChangeList {
    return addChangeList(name, comment)
  }

  override fun blockModalNotifications() {
    throw UnsupportedOperationException()
  }

  override fun unblockModalNotifications() {
    throw UnsupportedOperationException()
  }

  override fun invokeAfterUpdate(
    afterUpdate: Runnable,
    mode: InvokeAfterUpdateMode,
    title: String?,
    state: ModalityState?,
  ) {
    throw UnsupportedOperationException()
  }

  override suspend fun awaitUpdate() {
    throw UnsupportedOperationException()
  }
}
