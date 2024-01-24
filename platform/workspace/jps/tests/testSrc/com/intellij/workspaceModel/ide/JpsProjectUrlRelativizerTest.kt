// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.facet.mock.AnotherMockFacetType
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.java.workspace.entities.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityStorageSerializer
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspaceModel.ide.impl.JpsProjectUrlRelativizer
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer
import com.intellij.workspaceModel.ide.impl.jps.serialization.LoadedProjectData
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import com.intellij.workspaceModel.ide.impl.jps.serialization.sampleFileBasedProjectFile
import org.junit.*
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

// Test setup is copied from other tests, such as DelayedProjectSynchronizerTest
class JpsProjectUrlRelativizerTest {

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  private lateinit var serializer: EntityStorageSerializer
  private lateinit var projectData: LoadedProjectData
  private lateinit var project: Project
  private lateinit var urlRelativizer: JpsProjectUrlRelativizer

  @Before
  fun setUp() {
    Registry.get("ide.workspace.model.store.relative.paths.in.cache").setValue(true)

    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    virtualFileManager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
    registerFacetType(MockFacetType(), disposableRule.disposable)
    registerFacetType(AnotherMockFacetType(), disposableRule.disposable)

    projectData = copyAndLoadProject(sampleFileBasedProjectFile, virtualFileManager)
    project = PlatformTestUtil.loadAndOpenProject(projectData.projectDir.toPath(), disposableRule.disposable)
    urlRelativizer = JpsProjectUrlRelativizer(project)
    serializer = EntityStorageSerializerImpl(
      WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver,
      virtualFileManager,
      urlRelativizer = JpsProjectUrlRelativizer(project)
    )
  }

  @After
  fun tearDown() {
    WorkspaceModelCacheImpl.testCacheFile = null
    Registry.get("ide.workspace.model.store.relative.paths.in.cache").resetToDefault()
  }

  @Test
  fun `check that required base paths exist`() {
    assertBasePathExistsWithProtocolsFor("\$PROJECT_DIR$")
    assertBasePathExistsWithProtocolsFor("\$GRADLE_REPOSITORY$")
    assertBasePathExistsWithProtocolsFor("\$MAVEN_REPOSITORY$")

    assertBasePathExistsWithProtocolsFor("\$USER_HOME$")
    assertBasePathExistsWithProtocolsFor("\$APPLICATION_HOME_DIR$")
    assertBasePathExistsWithProtocolsFor("\$APPLICATION_PLUGINS_DIR$")
    assertBasePathExistsWithProtocolsFor("\$APPLICATION_CONFIG_DIR$")
  }

  @Test
  fun `check conversion between relative and absolute paths for PROJECT_DIR`() {
    val pathsToTest = getPathsToTest(project.basePath!!)

    for (path in pathsToTest) {
      // Check conversion both ways
      val absoluteUrl = path.absoluteUrl
      val relativeUrl = path.relativeUrl
      val convertedRelativeUrl = urlRelativizer.toRelativeUrl(absoluteUrl)
      val convertedAbsoluteUrl = urlRelativizer.toAbsoluteUrl(relativeUrl)
      assertEquals(relativeUrl, convertedRelativeUrl,
                   "$absoluteUrl was converted to $convertedRelativeUrl while expecting $relativeUrl")
      assertEquals(absoluteUrl, convertedAbsoluteUrl,
                   "$relativeUrl was converted to $convertedAbsoluteUrl while expecting $absoluteUrl")
    }
  }

  @Test
  fun `check paths that are not under PROJECT_DIR`() {
    val pathsToTest = getPathsNotUnderProjectDir(project.basePath!!)

    for (path in pathsToTest) {
      assertFalse("$path should not have \$PROJECT_DIR$ when converted.") {
        val relativeUrl = urlRelativizer.toRelativeUrl(path)
        relativeUrl.contains("\$PROJECT_DIR$")
      }
    }
  }

  @Test
  fun `path should be system independent when converted`() {
    val projectDir = project.basePath!!
    val absoluteUrl = "$projectDir\\folder\\file.txt"
    val expectedRelativeUrl = "\$PROJECT_DIR$/folder/file.txt"

    assertEquals(expectedRelativeUrl, urlRelativizer.toRelativeUrl(absoluteUrl))
  }

  @Test
  fun `check loading cache in different project dir`() {
    // This test checks if relative paths work as expected: when entities
    // are serialized to cache, they should be saved as relative paths.
    // Because of that, when this cache is deserialized in other location (other computer)
    // the entities should have different absolute paths.
    // The test setup is rather arbitrary. However, I do not see any other way
    // to test this now.

    // Firstly, save current project entity storage to cache
    val storage1 = projectData.storage
    val cacheFile = tempDirectory.newFile("cache.data").toPath()
    WorkspaceModelCacheImpl.testCacheFile = cacheFile
    serializer.serializeCache(cacheFile, storage1)

    // Next, load project in another folder
    val otherProjectDir = tempDirectory.newDirectory()
    val otherProject = PlatformTestUtil.loadAndOpenProject(otherProjectDir.toPath(), disposableRule.disposable)
    assertNotEquals(otherProject.basePath!!, project.basePath!!)
    val otherPathRelativizer = JpsProjectUrlRelativizer(otherProject)

    // Finally, deserialize cache
    val otherSerializer = EntityStorageSerializerImpl(
      WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver,
      virtualFileManager,
      urlRelativizer = otherPathRelativizer
    )
    val storage2 = otherSerializer.deserializeCache(cacheFile).getOrNull()!!

    // Check paths in the storages
    val paths1 = getAbsolutePathsForEntities(storage1)
    val paths2 = getAbsolutePathsForEntities(storage2)
    // Firstly, check if relative paths (when converted) are the same
    assertEquals(paths1.map { urlRelativizer.toRelativeUrl(it) }.sorted(),
                 paths2.map { otherPathRelativizer.toRelativeUrl(it) }.sorted())
    // And then also check if relative paths (with projectDir prefix removed) are the same
    // just to be sure
    assertEquals(paths1.map { removeProtocolAndPrefixIfExists(it, project.basePath!!) }.sorted(),
                 paths2.map { removeProtocolAndPrefixIfExists(it, otherProject.basePath!!) }.sorted())
  }

  private fun getPathsToTest(projectDir: String): List<TestPath> {
    return listOf(
      TestPath("", ""),
      TestPath("/", "/"),
      TestPath(projectDir, "\$PROJECT_DIR$"),
      TestPath("$projectDir/", "\$PROJECT_DIR$/"),
      TestPath("file://$projectDir/folder/file.txt", "file://\$PROJECT_DIR$/folder/file.txt"),
    )
  }

  private fun getPathsNotUnderProjectDir(projectDir: String): List<String> {
    return listOf(
      "", "/", "$projectDir.txt", projectDir.substring(0, projectDir.length - 1)
    )
  }

  private fun assertBasePathExistsWithProtocolsFor(identifier: String) {
    if (basePathExists(identifier)) {
      assertBasePathExistsFor("file:$identifier")
      assertBasePathExistsFor("file:/$identifier")
      assertBasePathExistsFor("file://$identifier")
      assertBasePathExistsFor("jar:$identifier")
      assertBasePathExistsFor("jar:/$identifier")
      assertBasePathExistsFor("jar://$identifier")
      assertBasePathExistsFor("jrt:$identifier")
      assertBasePathExistsFor("jrt:/$identifier")
      assertBasePathExistsFor("jrt://$identifier")
    }
  }

  private fun assertBasePathExistsFor(identifier: String) {
    urlRelativizer.getAllBasePathIdentifiers().forEach { basePathIdentifier ->
      if (identifier == basePathIdentifier) return
    }
    fail("Base path with identifier $identifier not found.")
  }

  private fun basePathExists(identifier: String): Boolean {
    urlRelativizer.getAllBasePathIdentifiers().forEach { basePathIdentifier ->
      if (identifier == basePathIdentifier) return true
    }
    return false
  }

  private fun getAbsolutePathsForEntities(storage: EntityStorage): List<String> {
    val entitiesList = storage.entitiesBySource { true }
    val vfuUrls = mutableSetOf<VirtualFileUrl>()

    entitiesList.forEach { entity ->

      when (entity) {
        is SourceRootEntity -> vfuUrls.add(entity.url)
        is ExcludeUrlEntity -> vfuUrls.add(entity.url)
        is ContentRootEntity -> vfuUrls.add(entity.url)
        is FileCopyPackagingElementEntity -> vfuUrls.add(entity.filePath)
        is ArtifactEntity -> entity.outputUrl?.let { vfuUrls.add(it) }
        is ExtractedDirectoryPackagingElementEntity -> vfuUrls.add(entity.filePath)
        is DirectoryCopyPackagingElementEntity -> vfuUrls.add(entity.filePath)
        is JavaModuleSettingsEntity -> {
          entity.compilerOutput?.let { vfuUrls.add(it) }
          entity.compilerOutputForTests?.let { vfuUrls.add(it) }
        }
      }
    }

    return vfuUrls.map { it.url }
  }

  private fun removeProtocolAndPrefixIfExists(url: String, projectDir: String): String {
    var trimmedUrl = removePrefixIfExists(url, "file:")
    trimmedUrl = removePrefixIfExists(trimmedUrl, "jar:")
    trimmedUrl = removePrefixIfExists(trimmedUrl, "jrt:")
    trimmedUrl = removePrefixIfExists(trimmedUrl, "/")
    trimmedUrl = removePrefixIfExists(trimmedUrl, "/")
    trimmedUrl = removePrefixIfExists(trimmedUrl, "/")
    trimmedUrl = removePrefixIfExists("/$trimmedUrl", projectDir)
    return trimmedUrl
  }

  private fun removePrefixIfExists(url: String, prefix: String): String {
    return if (url.startsWith(prefix))
      url.substring(prefix.length)
    else url
  }

  companion object {
    @JvmField
    @ClassRule
    val applicationRule = ApplicationRule()
  }

  private inner class TestPath(val absoluteUrl: String, val relativeUrl: String)
}