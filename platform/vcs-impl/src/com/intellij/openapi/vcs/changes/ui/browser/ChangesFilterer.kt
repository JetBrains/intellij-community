// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui.browser

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.trimExpandText
import com.intellij.diff.lang.DiffIgnoredRangeProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull

@ApiStatus.Internal
class ChangesFilterer(val project: Project?, val listener: Listener) : Disposable {
  companion object {
    @JvmField
    val DATA_KEY: DataKey<ChangesFilterer> = DataKey.create("com.intellij.openapi.vcs.changes.ui.browser.ChangesFilterer")

    private val LOG = logger<ChangesFilterer>()
  }

  private val LOCK = Any()

  private val updateQueue = MergingUpdateQueue("ChangesFilterer", 300, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private var progressIndicator: ProgressIndicator = EmptyProgressIndicator()

  private var rawChanges: List<Change>? = null

  private var activeFilter: Filter? = null

  private var processedChanges: List<Change>? = null
  private var pendingChanges: List<Change>? = null
  private var filteredOutChanges: List<Change>? = null

  override fun dispose() {
    resetFilter()
  }

  @RequiresEdt
  fun setChanges(changes: List<Change>?) {
    val oldChanges = rawChanges
    if (oldChanges == null && changes == null) return
    if (oldChanges != null && changes != null && ContainerUtil.equalsIdentity(oldChanges, changes)) return
    rawChanges = changes?.toList()
    if (activeFilter != null) {
      restartLoading()
    }
  }

  @RequiresEdt
  fun getFilteredChanges(): FilteredState {
    synchronized(LOCK) {
      val processed = processedChanges
      val pending = pendingChanges
      val filteredOut = filteredOutChanges
      return when {
        processed != null && pending != null && filteredOut != null -> FilteredState.create(processed, pending, filteredOut)
        else -> FilteredState.create(rawChanges ?: emptyList())
      }
    }
  }

  @RequiresEdt
  fun getProgress(): Float {
    val pendingCount = synchronized(LOCK) { pendingChanges?.size }
    val totalCount = rawChanges?.size
    if (pendingCount == null || totalCount == null || totalCount == 0) return 1.0f

    return (1.0f - pendingCount.toFloat() / totalCount).coerceAtLeast(0.0f)
  }

  @NotNull
  fun hasActiveFilter(): Boolean = activeFilter != null

  fun clearFilter() = setFilter(null)

  private fun setFilter(filter: Filter?) {
    activeFilter = filter
    restartLoading()
  }

  private fun restartLoading() {
    val indicator = resetFilter()

    val filter = activeFilter
    val changesToFilter = rawChanges
    if (filter == null || changesToFilter == null) {
      updatePresentation()
      return
    }

    val changes = changesToFilter.sortedWith(ChangesComparator.getInstance(false))
    // Work around expensive removeAt(0) with double reversion
    val pending = changes.asReversed().asSequence().map { Change(it.beforeRevision, it.afterRevision, FileStatus.OBSOLETE) }
      .toMutableList().asReversed()
    val processed = mutableListOf<Change>()
    val filteredOut = mutableListOf<Change>()

    synchronized(LOCK) {
      processedChanges = processed
      pendingChanges = pending
      filteredOutChanges = filteredOut
    }

    updatePresentation()

    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(
        { filterChanges(changes, filter, pending, processed, filteredOut) },
        indicator)
    }
  }

  private fun filterChanges(changes: List<Change>,
                            filter: Filter,
                            pending: MutableList<Change>,
                            processed: MutableList<Change>,
                            filteredOut: MutableList<Change>) {
    queueUpdatePresentation()
    val filteredChanges = filter.acceptBulk(this, changes)
    if (filteredChanges != null) {
      synchronized(LOCK) {
        ProgressManager.checkCanceled()
        pending.clear()
        processed.addAll(filteredChanges)
        val filteredOutSet = changes.toMutableSet()
        filteredOutSet.removeAll(filteredChanges)
        filteredOut.addAll(filteredOutSet)
      }
      updatePresentation()
      return
    }

    for (change in changes) {
      ProgressManager.checkCanceled()

      val accept = try {
        filter.accept(this, change)
      }
      catch (e: VcsException) {
        LOG.warn(e)
        true
      }

      synchronized(LOCK) {
        ProgressManager.checkCanceled()
        pending.removeAt(0)
        if (accept) {
          processed.add(change)
        }
        else {
          filteredOut.add(change)
        }
      }

      queueUpdatePresentation()
    }
    updatePresentation()
  }

  private fun queueUpdatePresentation() {
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "update") {
      updatePresentation()
    })
  }

  private fun updatePresentation() {
    ModalityUiUtil.invokeLaterIfNeeded(
      ModalityState.any()) {
      updateQueue.cancelAllUpdates()
      listener.updateChanges()
    }
  }

  private fun resetFilter(): ProgressIndicator {
    synchronized(LOCK) {
      processedChanges = null
      pendingChanges = null
      filteredOutChanges = null

      progressIndicator.cancel()
      progressIndicator = EmptyProgressIndicator()
      return progressIndicator
    }
  }

  private interface Filter {
    fun isAvailable(filterer: ChangesFilterer): Boolean = true
    fun accept(filterer: ChangesFilterer, change: Change): Boolean

    fun acceptBulk(filterer: ChangesFilterer, changes: List<Change>): Collection<Change>? = null

    @Nls
    fun getText(): String

    @Nls
    fun getDescription(): String? = null
  }

  private object MovesOnlyFilter : Filter {
    override fun getText(): String = VcsBundle.message("action.filter.moved.files.text")

    override fun acceptBulk(filterer: ChangesFilterer, changes: List<Change>): Collection<Change>? {
      for (epFilter in BulkMovesOnlyChangesFilter.EP_NAME.extensionList) {
        val filteredChanges = epFilter.filter(filterer.project, changes)
        if (filteredChanges != null) {
          return filteredChanges
        }
      }
      return null
    }

    override fun accept(filterer: ChangesFilterer, change: Change): Boolean {
      val bRev = change.beforeRevision ?: return true
      val aRev = change.afterRevision ?: return true
      if (bRev.file == aRev.file) return true

      if (bRev is ByteBackedContentRevision && aRev is ByteBackedContentRevision) {
        val bytes1 = bRev.contentAsBytes ?: return true
        val bytes2 = aRev.contentAsBytes ?: return true
        return !bytes1.contentEquals(bytes2)
      }
      else {
        val content1 = bRev.content ?: return true
        val content2 = aRev.content ?: return true
        return content1 != content2
      }
    }
  }

  private object NonImportantFilter : Filter {
    override fun getText(): String = VcsBundle.message("action.filter.non.important.files.text")
    override fun isAvailable(filterer: ChangesFilterer): Boolean = filterer.project != null

    override fun accept(filterer: ChangesFilterer, change: Change): Boolean {
      val project = filterer.project
      val bRev = change.beforeRevision ?: return true
      val aRev = change.afterRevision ?: return true
      val content1 = bRev.content ?: return true
      val content2 = aRev.content ?: return true

      val diffContent1 = DiffContentFactory.getInstance().create(project, content1, bRev.file)
      val diffContent2 = DiffContentFactory.getInstance().create(project, content2, aRev.file)

      val provider = DiffIgnoredRangeProvider.EP_NAME.extensions.find {
        it.accepts(project, diffContent1) &&
        it.accepts(project, diffContent2)
      }
      if (provider == null) return content1 != content2

      val ignoredRanges1 = provider.getIgnoredRanges(project, content1, diffContent1)
      val ignoredRanges2 = provider.getIgnoredRanges(project, content2, diffContent2)

      val ignored1 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges1)
      val ignored2 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges2)

      val range = trimExpandText(content1, content2, 0, 0, content1.length, content2.length, ignored1, ignored2)
      return !range.isEmpty
    }
  }

  interface Listener {
    fun updateChanges()
  }

  class FilteredState private constructor(val changes: List<Change>, val pending: List<Change>, val filteredOut: List<Change>) {
    companion object {
      @JvmStatic
      fun create(changes: List<Change>): FilteredState = create(changes, emptyList(), emptyList())

      fun create(changes: List<Change>, pending: List<Change>, filteredOut: List<Change>): FilteredState =
        FilteredState(changes.toList(), pending.toList(), filteredOut.toList())
    }
  }

  class FilterGroup : ActionGroup(), Toggleable, DumbAware {
    init {
      isPopup = false
      templatePresentation.text = VcsBundle.message("action.filter.filter.by.text")
      templatePresentation.setIconSupplier { AllIcons.General.Filter }
      templatePresentation.isDisableGroupIfEmpty = false
    }

    override fun update(e: AnActionEvent) {
      val filterer = e.getData(DATA_KEY)
      if (filterer == null) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      e.presentation.isVisible = true
      e.presentation.isEnabled = filterer.rawChanges != null
      Toggleable.setSelected(e.presentation, filterer.activeFilter != null)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val filterer = e?.getData(DATA_KEY) ?: return AnAction.EMPTY_ARRAY

      return arrayOf<AnAction>(Separator(VcsBundle.message("action.filter.separator.text"))) +
             listOf(MovesOnlyFilter, NonImportantFilter)
               .filter { it.isAvailable(filterer) }
               .map { ToggleFilterAction(filterer, it) }
               .toTypedArray()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private class ToggleFilterAction(val filterer: ChangesFilterer, val filter: Filter)
    : ToggleAction(filter.getText(), filter.getDescription(), null), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = filterer.activeFilter == filter

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      filterer.setFilter(if (state) filter else null)
    }
  }
}
