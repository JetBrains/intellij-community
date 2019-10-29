package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsProjectStoragePlace
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImlReplaceBySourceTest {
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

    val storagePlace = JpsProjectStoragePlace.DirectoryBased(temp.root.toVirtualFileUrl())

    val builder = TypedEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadModule(moduleFile, storagePlace, builder)

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

    val replaceWith = TypedEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadModule(moduleFile, storagePlace, replaceWith)

    val before = builder.toStorage()

    builder.resetChanges()
    builder.replaceBySource({ true }, replaceWith.toStorage())

    val changes = builder.collectChanges(before).values.flatten()
    Assert.assertEquals(3, changes.size)

    Assert.assertEquals(3, (changes[0] as EntityChange.Replaced<ModuleEntity>).oldEntity.dependencies.size)
    Assert.assertEquals(2, (changes[0] as EntityChange.Replaced<ModuleEntity>).newEntity.dependencies.size)

    Assert.assertEquals(File(temp.root, "src2").toVirtualFileUrl().url, (changes[1] as EntityChange.Added<SourceRootEntity>).entity.url.url)
    Assert.assertEquals(true, (changes[2] as EntityChange.Added<JavaSourceRootEntity>).entity.generated)
  }

  private fun replaceBySourceFullReplace(projectFile: File) {
    val storageBuilder1 = TypedEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadProject(projectFile.asStoragePlace(), storageBuilder1)

    val storageBuilder2 = TypedEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadProject(projectFile.asStoragePlace(), storageBuilder2)

    //println(storageBuilder1.toGraphViz())
    //println(storageBuilder2.toGraphViz())

    val before = storageBuilder1.toStorage()
    storageBuilder1.resetChanges()
    storageBuilder1.replaceBySource(sourceFilter = { true }, replaceWith = storageBuilder2.toStorage())
    storageBuilder1.checkConsistency()

    val changes = storageBuilder1.collectChanges(before)
    Assert.assertTrue(changes.isEmpty())
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
