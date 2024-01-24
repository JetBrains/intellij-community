// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.tests.checkConsistency
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.stateStore
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.io.write
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Test
import java.io.File

class JpsProjectEntitiesLoaderTest : HeavyPlatformTestCase() {
  @Test
  fun `test load project`() {
    val storage = loadProject(sampleDirBasedProjectFile)
    checkSampleProjectConfiguration(storage, sampleDirBasedProjectFile)
  }

  @Test
  fun `test load project in ipr format`() {
    val projectFile = sampleFileBasedProjectFile
    val storage = loadProject(projectFile)
    storage.checkConsistency()
    checkSampleProjectConfiguration(storage, projectFile.parentFile)
  }

  @Test
  fun `test load module without NewModuleRootManager component`() {
    val moduleFile = project.stateStore.projectBasePath.resolve("xxx.iml")
    moduleFile.write("""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
      </module>
    """.trimIndent())

    val module = WriteAction.computeAndWait<Module, Exception> { ModuleManager.getInstance(project).loadModule(moduleFile) }
    val orderEntries = ModuleRootManager.getInstance(module).orderEntries
    assertEquals(1, orderEntries.size)
    assertTrue(orderEntries[0] is ModuleSourceOrderEntry)
  }

  @Test
  fun `test load module with empty NewModuleRootManager component`() {
    val moduleFile = project.stateStore.projectBasePath.resolve("xxx.iml")
    moduleFile.write("""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" />
      </module>
    """.trimIndent())

    val module = runWriteActionAndWait { ModuleManager.getInstance(project).loadModule(moduleFile) }
    val orderEntries = ModuleRootManager.getInstance(module).orderEntries
    assertEquals(1, orderEntries.size)
    assertTrue(orderEntries[0] is ModuleSourceOrderEntry)
  }

  private fun checkSampleProjectConfiguration(storage: EntityStorage, projectDir: File) {
    val projectUrl = projectDir.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager())
    val modules = storage.entities(ModuleEntity::class.java).sortedBy { it.name }.toList()
    assertEquals(3, modules.size)

    val mainModule = modules[0]
    assertEquals("main", mainModule.name)
    assertNull(mainModule.customImlData)
    val mainJavaSettings = mainModule.javaSettings!!
    assertEquals(true, mainJavaSettings.inheritedCompilerOutput)
    assertEquals(true, mainJavaSettings.excludeOutput)
    assertNull(mainJavaSettings.compilerOutput)
    assertNull(mainJavaSettings.compilerOutputForTests)
    assertEquals(projectDir.absolutePath, JpsPathUtil.urlToOsPath(assertOneElement(mainModule.contentRoots.toList()).url.url))
    val mainModuleSrc = assertOneElement(mainModule.sourceRoots.toList())
    assertEquals(File(projectDir, "src").absolutePath, JpsPathUtil.urlToOsPath(mainModuleSrc.url.url))
    assertEquals(JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID, mainModuleSrc.rootType)

    assertEquals(6, mainModule.dependencies.size)
    assertEquals(ModuleDependencyItem.InheritedSdkDependency, mainModule.dependencies[0])
    assertEquals(ModuleDependencyItem.ModuleSourceDependency, mainModule.dependencies[1])
    assertEquals("log4j", (mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).library.name)
    assertFalse((mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).exported)
    assertEquals(ModuleDependencyItem.DependencyScope.COMPILE,
                 (mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).scope)
    assertEquals(ModuleDependencyItem.DependencyScope.TEST,
                 (mainModule.dependencies[3] as ModuleDependencyItem.Exportable.LibraryDependency).scope)
    assertTrue((mainModule.dependencies[4] as ModuleDependencyItem.Exportable.LibraryDependency).exported)
    assertEquals("util", (mainModule.dependencies[5] as ModuleDependencyItem.Exportable.ModuleDependency).module.name)

    val utilModule = modules[1]
    assertEquals("util", utilModule.name)
    assertEquals("""<component>
  <annotation-paths>
    <root url="$projectUrl/lib/anno" />
  </annotation-paths>
  <javadoc-paths>
    <root url="$projectUrl/lib/javadoc" />
  </javadoc-paths>
</component>""", utilModule.customImlData!!.rootManagerTagCustomData)
    val utilJavaSettings = utilModule.javaSettings!!
    assertEquals(false, utilJavaSettings.inheritedCompilerOutput)
    assertEquals(true, utilJavaSettings.excludeOutput)
    assertEquals("JDK_1_7", utilJavaSettings.languageLevelId)
    assertEquals("$projectUrl/out/production-util", utilJavaSettings.compilerOutput?.url)
    assertEquals("$projectUrl/out/test-util", utilJavaSettings.compilerOutputForTests?.url)
    val utilContentRoot = assertOneElement(utilModule.contentRoots.toList())
    assertEquals("$projectUrl/util", utilContentRoot.url.url)
    assertEquals("$projectUrl/util/exc", assertOneElement(utilContentRoot.excludedUrls).url.url)
    assertEquals(listOf("*.xml", "cvs"), utilContentRoot.excludedPatterns)
    val utilModuleSrc = assertOneElement(utilModule.sourceRoots.toList())
    assertEquals("$projectUrl/util/src", utilModuleSrc.url.url)
    val utilModuleLibraries = utilModule.getModuleLibraries(storage).sortedBy { it.name }.toList()
    val log4jModuleLibrary = utilModuleLibraries[1]
    assertEquals("log4j", log4jModuleLibrary.name)
    val log4jRoot = log4jModuleLibrary.roots[0]
    assertTrue(log4jRoot.url.url.contains("log4j.jar"))
    assertEquals("CLASSES", log4jRoot.type.name)
    assertEquals(LibraryRoot.InclusionOptions.ROOT_ITSELF, log4jRoot.inclusionOptions)


    assertEquals("xxx", modules[2].name)
    assertNull(modules[2].customImlData)
    val xxxJavaSettings = modules[2].javaSettings!!
    assertEquals(false, xxxJavaSettings.inheritedCompilerOutput)
    assertEquals(true, xxxJavaSettings.excludeOutput)
    assertEquals("$projectUrl/xxx/output", xxxJavaSettings.compilerOutput?.url)
    assertNull(xxxJavaSettings.compilerOutputForTests)

    val projectLibraries = storage.projectLibraries.sortedBy { it.name }.toList()
    assertEquals("jarDir", projectLibraries[0].name)
    val roots = projectLibraries[0].roots
    assertEquals(2, roots.size)
    assertEquals("CLASSES", roots[0].type.name)
    assertEquals(LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT, roots[0].inclusionOptions)
    assertEquals("SOURCES", roots[1].type.name)
    assertEquals(LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT, roots[1].inclusionOptions)
    val junitProjectLibrary = projectLibraries[1]
    assertEquals("junit", junitProjectLibrary.name)

    val artifacts = storage.entities(ArtifactEntity::class.java).sortedBy { it.name }.toList()
    assertEquals(2, artifacts.size)

    assertEquals("dir", artifacts[0].name)
    assertTrue(artifacts[0].includeInProjectBuild)
    assertEquals(File(projectDir, "out/artifacts/dir").absolutePath, JpsPathUtil.urlToOsPath(artifacts[0].outputUrl!!.url))
    val children = artifacts[0].rootElement!!.children.sortedBy { it::class.qualifiedName }.toList()
    assertEquals(2, children.size)
    val innerJar = children[0] as ArchivePackagingElementEntity
    assertEquals("x.jar", innerJar.fileName)
    assertEquals(utilModule, (innerJar.children.single() as ModuleOutputPackagingElementEntity).module!!.resolve(storage))
    val innerDir = children[1] as DirectoryPackagingElementEntity
    assertEquals("lib", innerDir.directoryName)
    val innerChildren = innerDir.children.toList()
    assertEquals(5, innerChildren.size)
    assertNotNull(innerChildren.find { it is LibraryFilesPackagingElementEntity && it.library?.resolve(storage) == log4jModuleLibrary })
    assertNotNull(innerChildren.find { it is LibraryFilesPackagingElementEntity && it.library?.resolve(storage) == junitProjectLibrary })
    assertEquals(File(projectDir, "main.iml").absolutePath, JpsPathUtil.urlToOsPath(
      innerChildren.filterIsInstance<FileCopyPackagingElementEntity>().single().filePath.url))
    assertEquals(File(projectDir, "lib/junit-anno").absolutePath, JpsPathUtil.urlToOsPath(
      innerChildren.filterIsInstance<DirectoryCopyPackagingElementEntity>().single().filePath.url))
    innerChildren.filterIsInstance<ExtractedDirectoryPackagingElementEntity>().single().let {
      assertEquals(File(projectDir, "lib/junit.jar").absolutePath, JpsPathUtil.urlToOsPath(it.filePath.url))
      assertEquals("/junit/", it.pathInArchive)
    }

    assertEquals("jar", artifacts[1].name)
    assertEquals(File(projectDir, "out/artifacts/jar").absolutePath, JpsPathUtil.urlToOsPath(artifacts[1].outputUrl!!.url))
    val archiveRoot = artifacts[1].rootElement!! as ArchivePackagingElementEntity
    assertEquals("jar.jar", archiveRoot.fileName)
    val archiveChildren = archiveRoot.children.toList()
    assertEquals(3, archiveChildren.size)
    assertEquals(artifacts[0],
                 archiveChildren.filterIsInstance<ArtifactOutputPackagingElementEntity>().single().artifact!!.resolve(storage))
  }

  @Test
  fun `test custom packaging elements`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/customPackagingElements/javaeeSampleProject.ipr")
    val storage = loadProject(projectDir)
    val artifacts = storage.entities(ArtifactEntity::class.java).sortedBy { it.name }.toList()
    assertEquals(6, artifacts.size)
    assertEquals("javaeeSampleProject:war exploded", artifacts[5].name)
    val artifactChildren = artifacts[5].rootElement!!.children.toList().sortedBy { it::class.qualifiedName }
    assertEquals(2, artifactChildren.size)
    val customElement = artifactChildren.filterIsInstance<CustomPackagingElementEntity>().single()
    assertEquals("javaee-facet-resources", customElement.typeId)
    assertEquals("<element id=\"javaee-facet-resources\" facet=\"javaeeSampleProject/web/Web\" />", customElement.propertiesXmlTag)
  }

  fun `test custom source root`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/customSourceRoot/customSourceRoot.ipr")
    val storage = loadProject(projectDir)
    val module = assertOneElement(storage.entities(ModuleEntity::class.java).toList())
    val sourceRoot = assertOneElement(module.sourceRoots.toList())
    assertEquals("erlang-include", sourceRoot.rootType)
    assertNull(sourceRoot.customSourceRootProperties)
  }

  fun `test load facets`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome("platform/workspace/jps/tests/testData/serialization/facets/facets.ipr")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).associateBy { it.name }
    val single = modules.getValue("single").facets.single()
    assertEquals("foo", single.facetType)
    assertEquals("Foo", single.name)
    assertEquals("""
                    <configuration>
                      <data />
                    </configuration>""".trimIndent(), single.configurationXmlTag)

    val two = modules.getValue("two").facets.toList()
    assertEquals(setOf("a", "b"), two.mapTo(HashSet()) { it.name })

    val twoReversed = modules.getValue("two.reversed").facets.toList()
    assertEquals(setOf("a", "b"), twoReversed.mapTo(HashSet()) { it.name })

    val subFacets = modules.getValue("subFacets").facets.sortedBy { it.name }.toList()
    assertEquals(listOf("Bar", "Foo"), subFacets.map { it.name })
    val (bar, foo) = subFacets
    assertEquals("Foo", bar.underlyingFacet!!.name)
    assertEquals("""
                    <configuration>
                      <data />
                    </configuration>""".trimIndent(), foo.configurationXmlTag)
    assertEquals("""
                    <configuration>
                      <data2 />
                    </configuration>""".trimIndent(), bar.configurationXmlTag)
  }

  private fun loadProject(projectFile: File): EntityStorage {
    val storageBuilder = MutableEntityStorage.create()
    val virtualFileManager: VirtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, storageBuilder, virtualFileManager)
    return storageBuilder.toSnapshot()
  }
}
