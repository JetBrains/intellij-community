// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager.Companion.INITIAL_LAYOUT_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val MIGRATION_PROPERTY = "toolwindow.default.layout.migration.version"
private const val NAMED_LAYOUT = "Debugging"

@TestApplication
internal class ToolWindowDefaultLayoutManagerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  @AfterEach
  fun clearLocalMarker() {
    PropertiesComponent.getInstance().unsetValue(MIGRATION_PROPERTY)
  }

  @Test
  fun `the default layout of the classic UI shows the Project view`() {
    assertDefaultLayoutShowsProjectView(isNewUi = false)
  }

  @Test
  fun `the default layout of the new UI shows the Project view`() {
    assertDefaultLayoutShowsProjectView(isNewUi = true)
  }

  private fun assertDefaultLayoutShowsProjectView(isNewUi: Boolean) {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = isNewUi)

    assertThat(manager.getFactoryDefaultLayoutCopy().getInfo(ToolWindowId.PROJECT_VIEW)!!.isVisible).isTrue()

    manager.noStateLoaded()
    assertThat(manager.getLayoutCopy().getInfo(ToolWindowId.PROJECT_VIEW)!!.isVisible).isTrue()
  }

  @Test
  fun `the classic UI default layout equals the output of the former Rider provider`() {
    assertDefaultLayoutEqualsFormerRiderOutput(isNewUi = false)
  }

  @Test
  fun `the new UI default layout equals the output of the former Rider provider`() {
    assertDefaultLayoutEqualsFormerRiderOutput(isNewUi = true)
  }

  /**
   * The former Rider provider showed the Project view on top of the platform provider.
   * Its body is copied here, so the test proves that the platform defaults make it redundant.
   */
  private fun assertDefaultLayoutEqualsFormerRiderOutput(isNewUi: Boolean) {
    val platformLayout = getDefaultLayoutToolWindowDescriptors(isNewUi)

    @Suppress("DEPRECATION")
    val formerRiderProvider = object : DefaultToolWindowLayoutProvider {
      private val original = IntellijPlatformDefaultToolWindowLayoutProvider()

      override fun createV1Layout(): List<ToolWindowDescriptor> {
        return original.createV1Layout().apply { single { it.id == ToolWindowId.PROJECT_VIEW }.isVisible = true }
      }

      override fun createV2Layout(): List<ToolWindowDescriptor> {
        return original.createV2Layout().apply { single { it.id == ToolWindowId.PROJECT_VIEW }.isVisible = true }
      }
    }
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().replaceService(DefaultToolWindowLayoutProvider::class.java, formerRiderProvider, disposable)

    val riderLayout = getDefaultLayoutToolWindowDescriptors(isNewUi)

    assertThat(riderLayout).isEqualTo(platformLayout)
    assertThat(platformLayout.single { it.id == ToolWindowId.PROJECT_VIEW }.isVisible).isTrue()
  }

  @Test
  fun `the migration shows the Project view in both variants of the Custom active layout once`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    manager.loadState(storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView()))
    val modificationCount = manager.stateModificationCount

    manager.initializeComponent()

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = false)).isTrue()
    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isTrue()
    assertThat(manager.isProjectViewVisible(NAMED_LAYOUT, isNewUi = true)).isFalse()
    assertThat(manager.stateModificationCount).isGreaterThan(modificationCount)
    assertThat(manager.state.defaultLayoutMigrationVersion).isEqualTo(1)
    assertThat(PropertiesComponent.getInstance().getInt(MIGRATION_PROPERTY, 0)).isEqualTo(1)

    val nextSession = ToolWindowDefaultLayoutManager(isNewUi = true)
    nextSession.loadState(storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView()))
    nextSession.initializeComponent()
    assertThat(nextSession.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isFalse()
  }

  @Test
  fun `the migration keeps a named active layout`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    val stored = storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView())
    manager.loadState(stored.copy(activeLayoutName = NAMED_LAYOUT))

    manager.initializeComponent()

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isFalse()
    assertThat(manager.isProjectViewVisible(NAMED_LAYOUT, isNewUi = true)).isFalse()
    assertThat(manager.state.defaultLayoutMigrationVersion).isEqualTo(1)
    assertThat(PropertiesComponent.getInstance().getInt(MIGRATION_PROPERTY, 0)).isEqualTo(1)
  }

  @Test
  fun `the local marker stops the migration`() {
    PropertiesComponent.getInstance().setValue(MIGRATION_PROPERTY, 1, 0)
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    manager.loadState(storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView()))
    val modificationCount = manager.stateModificationCount

    manager.initializeComponent()

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isFalse()
    assertThat(manager.stateModificationCount).isEqualTo(modificationCount)
  }

  @Test
  fun `the roamed marker stops the migration and writes the local marker`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    val stored = storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView())
    manager.loadState(stored.copy(defaultLayoutMigrationVersion = 1))
    val modificationCount = manager.stateModificationCount

    manager.initializeComponent()

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isFalse()
    assertThat(manager.stateModificationCount).isEqualTo(modificationCount)
    assertThat(PropertiesComponent.getInstance().getInt(MIGRATION_PROPERTY, 0)).isEqualTo(1)
  }

  @Test
  fun `the migration keeps an empty variant empty`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    manager.loadState(storedState(v1 = emptyList(), v2 = layoutWithHiddenProjectView()))

    manager.initializeComponent()

    assertThat(manager.state.layouts.getValue(INITIAL_LAYOUT_NAME).v1).isEmpty()
    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isTrue()
  }

  /**
   * `noStateLoaded` copies the layout of `WindowManagerImpl.oldLayout` with this `setLayout` call.
   * A headless test has no `WindowManagerImpl`.
   */
  @Test
  fun `the migration runs after the copy of the old layout`() {
    val oldLayout = ToolWindowDefaultLayoutManager(isNewUi = false).also { it.noStateLoaded() }.getLayoutCopy()
    oldLayout.getInfo(ToolWindowId.PROJECT_VIEW)!!.isVisible = false
    val manager = ToolWindowDefaultLayoutManager(isNewUi = false)
    manager.setLayout(INITIAL_LAYOUT_NAME, oldLayout)
    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = false)).isFalse()

    manager.initializeComponent()

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = false)).isTrue()
  }

  @Test
  fun `the migration does not save a fresh default layout`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    manager.noStateLoaded()

    manager.initializeComponent()

    assertThat(manager.stateModificationCount).isZero()
    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isTrue()
    assertThat(PropertiesComponent.getInstance().getInt(MIGRATION_PROPERTY, 0)).isEqualTo(1)
  }

  /** The Rider per-app manager builds its delegate with a constructor and calls only `loadState` and `noStateLoaded` on it. */
  @Test
  fun `a manager that the store does not initialize keeps its layout`() {
    val manager = ToolWindowDefaultLayoutManager(isNewUi = true)
    manager.loadState(storedState(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView()))

    assertThat(manager.isProjectViewVisible(INITIAL_LAYOUT_NAME, isNewUi = true)).isFalse()
    assertThat(manager.state.defaultLayoutMigrationVersion).isZero()
    assertThat(PropertiesComponent.getInstance().getValue(MIGRATION_PROPERTY)).isNull()
  }
}

private fun storedState(v1: List<ToolWindowDescriptor>, v2: List<ToolWindowDescriptor>): ToolWindowLayoutStorageManagerState {
  return ToolWindowLayoutStorageManagerState(
    activeLayoutName = INITIAL_LAYOUT_NAME,
    layouts = mapOf(
      INITIAL_LAYOUT_NAME to ToolWindowLayoutDescriptor(v1 = v1, v2 = v2),
      NAMED_LAYOUT to ToolWindowLayoutDescriptor(v1 = layoutWithHiddenProjectView(), v2 = layoutWithHiddenProjectView()),
    ),
  )
}

private fun layoutWithHiddenProjectView(): List<ToolWindowDescriptor> {
  return listOf(
    ToolWindowDescriptor(id = ToolWindowId.PROJECT_VIEW, order = 0, anchor = ToolWindowDescriptor.ToolWindowAnchor.LEFT, isVisible = false),
    ToolWindowDescriptor(id = ToolWindowId.COMMIT, order = 1, anchor = ToolWindowDescriptor.ToolWindowAnchor.LEFT, isVisible = false),
    ToolWindowDescriptor(id = ToolWindowId.TODO_VIEW, order = 0, anchor = ToolWindowDescriptor.ToolWindowAnchor.BOTTOM, isVisible = true),
  )
}

private fun ToolWindowDefaultLayoutManager.isProjectViewVisible(layoutName: String, isNewUi: Boolean): Boolean {
  val descriptor = state.layouts.getValue(layoutName)
  return (if (isNewUi) descriptor.v2 else descriptor.v1).single { it.id == ToolWindowId.PROJECT_VIEW }.isVisible
}
