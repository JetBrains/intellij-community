package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspaceModel.ide.impl.jps.serialization.asConfigLocation
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.storage.*
import org.junit.*
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImlReplaceBySourceTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
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

  @Suppress("UNCHECKED_CAST")
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

    val configLocation = JpsProjectConfigLocation.DirectoryBased(temp.root.toVirtualFileUrl(virtualFileManager))

    val builder = WorkspaceEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadModule(moduleFile, configLocation, builder, virtualFileManager)

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

    val replaceWith = WorkspaceEntityStorageBuilder.create()
    val source = builder.entities(ModuleEntity::class.java).first().entitySource as JpsFileEntitySource.FileInDirectory
    JpsProjectEntitiesLoader.loadModule(moduleFile, source, configLocation, replaceWith, virtualFileManager)

    val before = builder.toStorage()

    builder.resetChanges()
    builder.replaceBySource({ true }, replaceWith.toStorage())

    val changes = builder.collectChanges(before).values.flatten()
    Assert.assertEquals(5, changes.size)

    val moduleChange = changes.filterIsInstance<EntityChange.Replaced<ModuleEntity>>().single()
    Assert.assertEquals(3, moduleChange.oldEntity.dependencies.size)
    Assert.assertEquals(2, moduleChange.newEntity.dependencies.size)

    // Changes 1 & 2 handle source roots ordering [ModuleSerializersFactory.SourceRootOrderEntry]
    @Suppress("USELESS_IS_CHECK")
    val sourceRootChange = changes.filterIsInstance<EntityChange.Added<SourceRootEntity>>().single { it.entity is SourceRootEntity }
    @Suppress("USELESS_IS_CHECK")
    val javaSourceRootChange = changes.filterIsInstance<EntityChange.Added<JavaSourceRootEntity>>().single { it.entity is JavaSourceRootEntity }
    Assert.assertEquals(File(temp.root, "src2").toVirtualFileUrl(virtualFileManager).url, sourceRootChange.entity.url.url)
    Assert.assertEquals(true, javaSourceRootChange.entity.generated)
  }

  private fun replaceBySourceFullReplace(projectFile: File) {
    val storageBuilder1 = WorkspaceEntityStorageBuilder.create()
    val data = com.intellij.workspaceModel.ide.impl.jps.serialization.loadProject(projectFile.asConfigLocation(virtualFileManager),
                                                                                  storageBuilder1, virtualFileManager)

    val storageBuilder2 = WorkspaceEntityStorageBuilder.create()
    val reader = CachingJpsFileContentReader(projectFile.asConfigLocation(virtualFileManager).baseDirectoryUrlString)
    data.loadAll(reader, storageBuilder2)

    //println(storageBuilder1.toGraphViz())
    //println(storageBuilder2.toGraphViz())

    val before = storageBuilder1.toStorage()
    storageBuilder1.resetChanges()
    storageBuilder1.checkConsistency()
    storageBuilder1.replaceBySource(sourceFilter = { true }, replaceWith = storageBuilder2.toStorage())
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
