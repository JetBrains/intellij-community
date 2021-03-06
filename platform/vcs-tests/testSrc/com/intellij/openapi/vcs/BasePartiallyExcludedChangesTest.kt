// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.PartiallyExcludedFilesStateHolder
import com.intellij.openapi.vcs.ex.ExclusionState
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.PartialChangesUtil

abstract class BasePartiallyExcludedChangesTest : BaseLineStatusTrackerManagerTest() {
  private lateinit var stateHolder: MyStateHolder

  override fun setUp() {
    super.setUp()
    stateHolder = MyStateHolder()
    stateHolder.updateExclusionStates()
  }

  protected inner class MyStateHolder : PartiallyExcludedFilesStateHolder<FilePath>(getProject()) {
    val paths = HashSet<FilePath>()

    init {
      Disposer.register(testRootDisposable, this)
    }

    override val trackableElements: Sequence<FilePath> get() = paths.asSequence()

    override fun getChangeListId(element: FilePath): String = DEFAULT.asListNameToId()

    override fun findElementFor(tracker: PartialLocalLineStatusTracker, changeListId: String): FilePath? {
      return paths.find { it.virtualFile == tracker.virtualFile }
    }

    override fun findTrackerFor(element: FilePath): PartialLocalLineStatusTracker? {
      val file = element.virtualFile ?: return null
      return PartialChangesUtil.getPartialTracker(getProject(), file)
    }

    fun toggleElements(elements: Collection<FilePath>) {
      val hasExcluded = elements.any { getExclusionState(it) != ExclusionState.ALL_INCLUDED }
      if (hasExcluded) includeElements(elements) else excludeElements(elements)
    }

    fun waitExclusionStateUpdate() {
      myUpdateQueue.flush()
    }
  }


  protected fun setHolderPaths(vararg paths: String) {
    stateHolder.paths.clear()
    stateHolder.paths.addAll(paths.toFilePaths())
  }

  protected fun waitExclusionStateUpdate() {
    stateHolder.waitExclusionStateUpdate()
  }

  protected fun toggle(vararg paths: String) {
    assertContainsElements(stateHolder.paths, paths.toFilePaths())
    stateHolder.toggleElements(paths.toFilePaths())
  }

  protected fun include(vararg paths: String) {
    assertContainsElements(stateHolder.paths, paths.toFilePaths())
    stateHolder.includeElements(paths.toFilePaths())
  }

  protected fun exclude(vararg paths: String) {
    assertContainsElements(stateHolder.paths, paths.toFilePaths())
    stateHolder.excludeElements(paths.toFilePaths())
  }

  protected fun assertIncluded(vararg paths: String) {
    val expected = paths.toFilePaths().toSet()
    val actual = stateHolder.getIncludedSet()
    assertSameElements(actual.map { it.name }, expected.map { it.name })
    assertSameElements(actual, expected)
  }

  protected fun PartialLocalLineStatusTracker.assertExcluded(index: Int, expected: Boolean) {
    val range = this.getRanges()!![index]
    assertEquals(expected, range.isExcludedFromCommit)
  }

  protected fun String.assertExcludedState(holderState: ExclusionState, trackerState: ExclusionState = holderState) {
    val actual = stateHolder.getExclusionState(this.toFilePath)
    assertEquals(holderState, actual)

    (toFilePath.virtualFile?.tracker as? PartialLocalLineStatusTracker)?.assertExcludedState(trackerState, DEFAULT)
  }

  protected fun PartialLocalLineStatusTracker.exclude(index: Int, isExcluded: Boolean) {
    val ranges = getRanges()!!
    this.setExcludedFromCommit(ranges[index], isExcluded)
    waitExclusionStateUpdate()
  }

  protected fun PartialLocalLineStatusTracker.moveTo(index: Int, changelistName: String) {
    val ranges = getRanges()!!
    this.moveToChangelist(ranges[index], changelistName.asListNameToList())
    waitExclusionStateUpdate()
  }
}