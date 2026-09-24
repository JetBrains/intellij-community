// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.projectView.backend.impl.scope.ScopePaneModel
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.vcs.changes.ChangeListScope
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * The "All Changed Files" scope pane has to follow the VCS, end to end.
 *
 * That scope ([ChangeListScope]) is nothing but the live predicate `ChangeListManager::isFileAffected`, so its
 * contents change without any event of its own: `ChangeListScopeViewUpdater.changeListsChanged` refreshes the
 * legacy `ScopeViewPane` directly instead of firing the scope listeners `ScopePaneProvider` subscribes to, and
 * the `fileStatusesChanged` that does reach `ScopeViewTreeModel` only invalidates that model's own children,
 * not the per-node visibility `ProjectFileTreeModel` caches for the filter.
 *
 * Both tests therefore change only what the mock VCS reports, never the files themselves: a VFS event resets
 * that cached visibility through `ProjectFileNodeUpdater` and would hide the defect.
 *
 * Kept apart from [ProjectViewScopePaneTest] because these tests register a VCS in their project and mutate
 * what it reports.
 */
@TestApplication
internal class ProjectViewChangedFilesScopePaneTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy {
      projectViewTestDataPath(ProjectViewChangedFilesScopePaneTest::class.java, BLUEPRINT)
    }

    private const val ONLY_HELLO_CHANGED = """
      pvChangedFiles
       <content root>
        Hello.txt
    """

    private const val BOTH_CHANGED = """
      pvChangedFiles
       <content root>
        sub
         World.txt
        Hello.txt
    """

    private const val ONLY_WORLD_CHANGED = """
      pvChangedFiles
       <content root>
        sub
         World.txt
    """
  }

  // Instance-level fixtures, so that every test gets its own project: a shared one would also share the
  // registered VCS and the backend pane session (see ProjectViewPaneCompactPackagesTest). The directory names
  // are pinned, so the rendered tree is deterministic.
  //
  // The projects have to be opened, because VcsDirtyScopeManagerImpl.markEverythingDirty does nothing at all
  // while Project.isOpen is false, and a change list update would never be scheduled.
  private val appearingProject = projectFixture(
    pathFixture = tempPathFixture(subdirName = "pvChangedFiles"),
    openAfterCreation = true,
  )
  private val appearingSrcRoot = appearingProject.moduleFixture(name = "pvChangedFiles").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint,
  )

  private val disappearingProject = projectFixture(
    pathFixture = tempPathFixture(subdirName = "pvChangedFiles"),
    openAfterCreation = true,
  )
  private val disappearingSrcRoot = disappearingProject.moduleFixture(name = "pvChangedFiles").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = blueprint,
  )

  @Test
  fun `a file that becomes changed appears in the All Changed Files pane`() = timeoutRunBlocking(60.seconds) {
    val project = appearingProject.get()
    val src = appearingSrcRoot.get().virtualFile // materialize the project + module + source root
    val changes = setUpChangedFilesScope(project, src, changed = listOf("Hello.txt"))

    withProjectViewPane(project, changedFilesScopePaneId(project)) { pane ->
      pane.assertTreeWithContentRoot(ONLY_HELLO_CHANGED)

      changes.setChanged("Hello.txt", "sub/World.txt")
      refreshChangeLists(project)

      waitUntilAssertSucceeds("'sub/World.txt' has become changed and should appear in the pane", 30.seconds) {
        pane.assertTreeWithContentRoot(BOTH_CHANGED)
      }
    }
  }

  @Test
  fun `a file that becomes unchanged disappears from the All Changed Files pane`() = timeoutRunBlocking(60.seconds) {
    val project = disappearingProject.get()
    val src = disappearingSrcRoot.get().virtualFile
    val changes = setUpChangedFilesScope(project, src, changed = listOf("Hello.txt", "sub/World.txt"))

    withProjectViewPane(project, changedFilesScopePaneId(project)) { pane ->
      pane.assertTreeWithContentRoot(BOTH_CHANGED)

      changes.setChanged("sub/World.txt")
      refreshChangeLists(project)

      waitUntilAssertSucceeds("'Hello.txt' is no longer changed and should be gone from the pane", 30.seconds) {
        pane.assertTreeWithContentRoot(ONLY_WORLD_CHANGED)
      }
    }
  }

  /**
   * Registers a mock VCS over [src] reporting [changed] (paths relative to [src]) as modified, and waits until
   * the resulting "All Changed Files" pane exists. Runs before any pane is opened.
   */
  private suspend fun setUpChangedFilesScope(
    project: Project,
    src: VirtualFile,
    changed: List<String>,
  ): TestChangeProvider {
    // Otherwise whether ScopeViewTreeModel groups the content roots under a module node depends on when the
    // pane's settings accessor answers isShowModules, which makes the rendered tree non-deterministic. Set the
    // per-project state only, the way ProjectViewPaneCompactPackagesTest does.
    ProjectViewState.getInstance(project).showModules = false

    val provider = TestChangeProvider(src)
    val vcs = MockAbstractVcs(project).apply { setChangeProvider(provider) }
    val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
    vcsManager.registerVcs(vcs)
    vcsManager.directoryMappings = listOf(VcsDirectoryMapping(src.path, vcs.name))
    vcsManager.awaitInitialization()
    assertTrue(vcsManager.hasActiveVcss(), "ChangeListsScopesProvider reports no scopes without an active VCS")

    provider.setChanged(*changed.toTypedArray())
    refreshChangeLists(project)
    // Guard the setup itself, so that a mock VCS that stopped working fails here rather than further down,
    // where it would look like a pane that did not update.
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    for (path in changed) {
      assertTrue(
        changeListManager.isFileAffected(provider.resolve(path)),
        "The mock VCS should report '$path' as changed",
      )
    }

    // The scope only exists once a VCS is active, and the pane list is only rebuilt on a scope listener event.
    // This is the nudge ChangeListScopeViewUpdater.updateAvailableScopesList gives the legacy pane when
    // changelist availability changes; it is setup, and neither test relies on it afterwards.
    DependencyValidationManager.getInstance(project).fireScopeListeners()
    FrontendProjectViewPaneAggregator.getInstance(project).awaitPane(changedFilesScopePaneId(project))
    return provider
  }

  /**
   * Makes [ChangeListManager] pick up what the mock VCS reports now. Off the EDT, because both
   * [VcsDirtyScopeManager.markEverythingDirty] and the update behind `awaitUpdate` are background work.
   */
  private suspend fun refreshChangeLists(project: Project) {
    withContext(Dispatchers.Default) {
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      ChangeListManagerImpl.getInstanceImpl(project).awaitUpdate()
    }
  }

  /**
   * `ChangeListScope.ALL_CHANGED_FILES_SCOPE_NAME` is package-private, but with only the default changelist
   * `ChangesUtil.hasMeaningfulChangelists` is false, so "All Changed Files" is the only [ChangeListScope].
   */
  private fun changedFilesScopePaneId(project: Project): ProjectViewPaneId {
    val holder: NamedScopesHolder = DependencyValidationManager.getInstance(project)
    val scope = holder.scopes.first { it is ChangeListScope }
    return ScopePaneModel.paneId(NamedScopeFilter(holder, scope))
  }
}

/**
 * Reports the files last passed to [setChanged] as modified, whatever the dirty scope: the scope is always
 * everything here, and reporting more than was asked for is explicitly allowed by [ChangeProvider.getChanges].
 */
private class TestChangeProvider(private val src: VirtualFile) : ChangeProvider {
  @Volatile
  private var changed: List<VirtualFile> = emptyList()

  /** Replaces the whole set of changed files, addressed by their paths relative to the source root. */
  fun setChanged(vararg relativePaths: String) {
    changed = relativePaths.map { resolve(it) }
  }

  fun resolve(relativePath: String): VirtualFile =
    requireNotNull(src.findFileByRelativePath(relativePath)) { "No '$relativePath' under ${src.path}" }

  override fun getChanges(
    dirtyScope: VcsDirtyScope,
    builder: ChangelistBuilder,
    progress: ProgressIndicator,
    addGate: ChangeListManagerGate,
  ) {
    for (file in changed) {
      val path = VcsUtil.getFilePath(file)
      builder.processChange(Change(SimpleContentRevision("before", path, "0"), CurrentContentRevision(path)),
                            MockAbstractVcs.getKey())
    }
  }

  override fun isModifiedDocumentTrackingRequired(): Boolean = false

  override fun doCleanup(files: List<VirtualFile>) {
  }
}
