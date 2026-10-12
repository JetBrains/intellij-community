// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpen
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Test
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

@TestApplication
internal class ModulePathFilterTest {
  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture("src")
  }

  private val tempDirFixture = tempPathFixture()
  private val exampleModuleFixture = projectFixture.moduleFixture("intellij.example")

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()
  private val exampleModule get() = exampleModuleFixture.get()

  // Registry key tests
  @Test
  fun testRegistryKeyExists() {
    val value = Registry.`is`("devkit.module.hyperlink.show.chooser", false)
    assertThat(value).isFalse()
  }

  @Test
  fun testRegistryKeyCanBeChanged() {
    val registryValue = Registry.get("devkit.module.hyperlink.show.chooser")
    val originalValue = registryValue.asBoolean()
    try {
      registryValue.setValue(true)
      assertThat(Registry.`is`("devkit.module.hyperlink.show.chooser", false)).isTrue()

      registryValue.setValue(false)
      assertThat(Registry.`is`("devkit.module.hyperlink.show.chooser", false)).isFalse()
    }
    finally {
      registryValue.setValue(originalValue)
    }
  }

  @Test
  @RegistryKey(key = "devkit.module.hyperlink.show.chooser", value = "false")
  fun testHyperlinkInfoWithRegistryDisabled() {
    val hyperlinkInfo = ModuleFilesHyperlinkInfo(module)
    assertThat(hyperlinkInfo).isNotNull
  }

  @Test
  @RegistryKey(key = "devkit.module.hyperlink.show.chooser", value = "true")
  fun testHyperlinkInfoWithRegistryEnabled() {
    val hyperlinkInfo = ModuleFilesHyperlinkInfo(module)
    assertThat(hyperlinkInfo).isNotNull
  }

  // Pattern matching tests - test regex directly
  @Test
  fun testPatternMatchesIntellijModules() {
    assertThat(ModulePattern.matches("intellij.platform.core")).isTrue()
    assertThat(ModulePattern.matches("intellij.foo.bar.baz")).isTrue()
  }

  @Test
  fun testPatternMatchesKotlinModules() {
    assertThat(ModulePattern.matches("kotlin.base.plugin")).isTrue()
  }

  @Test
  fun testPatternMatchesFleetModules() {
    assertThat(ModulePattern.matches("fleet.rhizomedb.transactor")).isTrue()
  }

  @Test
  fun testPatternMatchesAndroidModules() {
    assertThat(ModulePattern.matches("android.tools.base")).isTrue()
  }

  @Test
  fun testPatternRequiresAtLeastTwoSegments() {
    assertThat(ModulePattern.matches("intellij")).isFalse()
    assertThat(ModulePattern.matches("kotlin")).isFalse()
  }

  @Test
  fun testPatternRejectsUnknownPrefixes() {
    assertThat(ModulePattern.matches("other.module.name")).isFalse()
    assertThat(ModulePattern.matches("com.example.plugin")).isFalse()
  }

  @Test
  fun testPatternFindsMultipleMatches() {
    val line = "intellij.foo.bar depends on kotlin.baz.qux"
    val matches = ModulePattern.findAll(line).toList()
    assertThat(matches).hasSize(2)
    assertThat(matches[0].value).isEqualTo("intellij.foo.bar")
    assertThat(matches[1].value).isEqualTo("kotlin.baz.qux")
  }

  // Filter tests - these don't need controlled module names
  @Test
  fun testPatternMatchesButModuleNotFound() {
    val filter = ModulePathFilter(project)
    val line = "Loading module intellij.nonexistent.module"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testNoMatchForNonModulePattern() {
    val filter = ModulePathFilter(project)
    val line = "Some random text without module names"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testNoMatchForSingleSegmentName() {
    val filter = ModulePathFilter(project)
    val line = "Loading module intellij"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testEntireLengthOffsetHandling() {
    val filter = ModulePathFilter(project)
    val line = "intellij.fake.module"
    val entireLength = 100
    val result = filter.applyFilter(line, entireLength)
    assertThat(result).isNull()
  }

  // Navigation tests
  @Test
  fun testNavigatesToModuleDescriptorInDirectoryResourceRoot() {
    val moduleXml = addResource("intellij.example.xml")
    addResourceRoot(moduleXml.parent)

    assertThat(navigateFromConsole()).isEqualTo(moduleXml)
  }

  @Test
  fun testNavigatesToPluginXmlInMetaInfDirectoryResourceRoot() {
    val pluginXml = addResource("META-INF/plugin.xml")
    addResourceRoot(pluginXml.parent, relativeOutputPath = "META-INF")

    assertThat(navigateFromConsole()).isEqualTo(pluginXml)
  }

  @Test
  fun testNavigatesToPluginXmlInSingleFileResourceRoot() {
    val pluginXml = addResource("META-INF/plugin.xml")
    addResourceRoot(pluginXml, relativeOutputPath = "META-INF")

    assertThat(navigateFromConsole()).isEqualTo(pluginXml)
  }

  @Test
  fun testNavigatesToModuleDescriptorInSingleFileResourceRoot() {
    val moduleXml = addResource("intellij.example.xml")
    addResourceRoot(moduleXml)

    assertThat(navigateFromConsole()).isEqualTo(moduleXml)
  }

  @Test
  fun testNoNavigationToDescriptorsOutsideTheirOutputPaths() {
    addResourceRoot(addResource("META-INF/plugin.xml").parent)
    addResourceRoot(addResource("sub/intellij.example.xml"), relativeOutputPath = "sub")

    assertThat(navigateFromConsole()).isNull()
  }

  private fun navigateFromConsole(): VirtualFile? {
    val line = "Loading module intellij.example"
    val hyperlinkInfo = ModulePathFilter(project)
      .applyFilter(line, line.length)!!
      .resultItems
      .single()
      .hyperlinkInfo!!
    return runInEdtAndGet {
      try {
        hyperlinkInfo.navigate(project)
        awaitPendingNavigationAndEditorOpen(project)
        FileEditorManagerEx.getInstanceEx(project).selectedFiles.singleOrNull()
      }
      finally {
        EditorTestUtil.closeAllFilesAndClearEditorHistory(project)
      }
    }
  }

  private fun addResource(path: String): VirtualFile {
    val file = tempDirFixture.get().resolve("resources/$path")
    file.createParentDirectories().writeText("<idea-plugin/>")
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
  }

  private fun addResourceRoot(root: VirtualFile, relativeOutputPath: String = "") {
    PsiTestUtil.addSourceRoot(exampleModule, root, JavaResourceRootType.RESOURCE, JavaResourceRootProperties(relativeOutputPath, false))
  }
}
