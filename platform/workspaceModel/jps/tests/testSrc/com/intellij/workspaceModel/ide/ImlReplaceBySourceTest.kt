// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.workspaceModel.jps.JpsProjectConfigLocation
import com.intellij.platform.workspaceModel.jps.JpsProjectFileEntitySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.TestErrorReporter
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.checkConsistency
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.toBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.*
import java.io.File

class ImlReplaceBySourceTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
  }

  @Test
  fun sampleProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    replaceBySourceFullReplace(projectDir)
  }

  @Test
  fun communityProject() {
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    replaceBySourceFullReplace(projectDir)
  }

  @Test
  fun addSourceRootToModule() {
    val moduleFile = temp.newFile("a.iml")
    moduleFile.writeText("""
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://${'$'}MODULE_DIR${'$'}">
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/src" isTestSource="false" />
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/testSrc" isTestSource="true" />
          </content>
          <orderEntry type="inheritedJdk" />
          <orderEntry type="sourceFolder" forTests="false" />
          <orderEntry type="library" name="kotlin-stdlib-jdk8" level="project" />
        </component>
      </module>
    """.trimIndent())

    val projectDir = temp.root.toVirtualFileUrl(virtualFileManager)
    val configLocation = JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))

    var builder = MutableEntityStorage.create()
    JpsProjectEntitiesLoader.loadModule(moduleFile.toPath(), configLocation, builder, TestErrorReporter,
                                        SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation)))

    moduleFile.writeText("""
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://${'$'}MODULE_DIR${'$'}">
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/src" isTestSource="false" />
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/src2" generated="true" />
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/testSrc" isTestSource="true" />
          </content>
          <orderEntry type="inheritedJdk" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent())

    val replaceWith = MutableEntityStorage.create()
    val source = builder.entities(ModuleEntity::class.java).first().entitySource as JpsProjectFileEntitySource.FileInDirectory
    JpsProjectEntitiesLoader.loadModule(moduleFile.toPath(), source, replaceWith, replaceWith, TestErrorReporter,
                                        SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation)))

    val before = builder.toSnapshot()

    builder = before.toBuilder()
    builder.replaceBySource({ true }, replaceWith.toSnapshot())

    val changes = builder.collectChanges(before).values.flatten()
    Assert.assertEquals(5, changes.size)

    val moduleChange = changes.filterIsInstance<EntityChange.Replaced<ModuleEntity>>().single()
    Assert.assertEquals(3, moduleChange.oldEntity.dependencies.size)
    Assert.assertEquals(2, moduleChange.newEntity.dependencies.size)

    // Changes 1 & 2 handle source roots ordering [ModuleSerializersFactory.SourceRootOrderEntry]
    @Suppress("USELESS_IS_CHECK")
    val sourceRootChange = changes.filterIsInstance<EntityChange.Added<SourceRootEntity>>().single { it.entity is SourceRootEntity }
    @Suppress("USELESS_IS_CHECK")
    val javaSourceRootChange = changes.filterIsInstance<EntityChange.Added<JavaSourceRootPropertiesEntity>>().single { it.entity is JavaSourceRootPropertiesEntity }
    Assert.assertEquals(File(temp.root, "src2").toVirtualFileUrl(virtualFileManager).url, sourceRootChange.entity.url.url)
    Assert.assertEquals(true, javaSourceRootChange.entity.generated)
  }

  private fun replaceBySourceFullReplace(projectFile: File) {
    var storageBuilder1 = MutableEntityStorage.create()
    val data = com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject(projectFile.asConfigLocation(virtualFileManager),
                                                                                  storageBuilder1, storageBuilder1, virtualFileManager)

    val storageBuilder2 = MutableEntityStorage.create()
    val reader = CachingJpsFileContentReader(projectFile.asConfigLocation(virtualFileManager))
    runBlocking {
      val builder = MutableEntityStorage.create()
      data.loadAll(reader, storageBuilder2, builder, builder, UnloadedModulesNameHolder.DUMMY, TestErrorReporter)
    }

    val before = storageBuilder1.toSnapshot()
    storageBuilder1 = before.toBuilder()
    storageBuilder1.checkConsistency()
    storageBuilder1.replaceBySource(sourceFilter = { true }, replaceWith = storageBuilder2.toSnapshot())
    storageBuilder1.checkConsistency()

    val changes = storageBuilder1.collectChanges(before)
    Assert.assertTrue(changes.toString(), changes.isEmpty())
  }

  @Rule
  @JvmField
  val temp = TempDirectory()

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
