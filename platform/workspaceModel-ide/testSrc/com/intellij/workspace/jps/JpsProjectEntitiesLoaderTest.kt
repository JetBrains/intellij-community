package com.intellij.workspace.jps

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspace.api.*
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
    checkSampleProjectConfiguration(storage, projectFile.parentFile)
  }

  private fun checkSampleProjectConfiguration(storage: TypedEntityStorage, projectDir: File) {
    val projectUrl = projectDir.toVirtualFileUrl()
    val modules = storage.entities(ModuleEntity::class).sortedBy { it.name }.toList()
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
    assertFalse(mainModuleSrc.tests)

    assertEquals(6, mainModule.dependencies.size)
    assertEquals(ModuleDependencyItem.InheritedSdkDependency, mainModule.dependencies[0])
    assertEquals(ModuleDependencyItem.ModuleSourceDependency, mainModule.dependencies[1])
    assertEquals("log4j", (mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).library.name)
    assertFalse((mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).exported)
    assertEquals(ModuleDependencyItem.DependencyScope.COMPILE, (mainModule.dependencies[2] as ModuleDependencyItem.Exportable.LibraryDependency).scope)
    assertEquals(ModuleDependencyItem.DependencyScope.TEST, (mainModule.dependencies[3] as ModuleDependencyItem.Exportable.LibraryDependency).scope)
    assertTrue((mainModule.dependencies[4] as ModuleDependencyItem.Exportable.LibraryDependency).exported)
    assertEquals("util", (mainModule.dependencies[5] as ModuleDependencyItem.Exportable.ModuleDependency).module.name)

    val utilModule = modules[1]
    assertEquals("util", utilModule.name)
    assertEquals("""<component LANGUAGE_LEVEL="JDK_1_7">
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
    assertEquals("$projectUrl/out/production-util", utilJavaSettings.compilerOutput?.url)
    assertEquals("$projectUrl/out/test-util", utilJavaSettings.compilerOutputForTests?.url)
    val utilContentRoot = assertOneElement(utilModule.contentRoots.toList())
    assertEquals("$projectUrl/util", utilContentRoot.url.url)
    assertEquals("$projectUrl/util/exc", assertOneElement(utilContentRoot.excludedUrls).url)
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

    val artifacts = storage.entities(ArtifactEntity::class).sortedBy { it.name }.toList()
    assertEquals(2, artifacts.size)

    assertEquals("dir", artifacts[0].name)
    assertTrue(artifacts[0].includeInProjectBuild)
    assertEquals(File(projectDir, "out/artifacts/dir").absolutePath, JpsPathUtil.urlToOsPath(artifacts[0].outputUrl.url))
    assertEquals(2, artifacts[0].rootElement.children.size)
    val innerJar = artifacts[0].rootElement.children[0] as ArchivePackagingElementEntity
    assertEquals("x.jar", innerJar.fileName)
    assertEquals(utilModule, (innerJar.children.single() as ModuleOutputPackagingElementEntity).module.resolve(storage))
    val innerDir = artifacts[0].rootElement.children[1] as DirectoryPackagingElementEntity
    assertEquals("lib", innerDir.directoryName)
    val innerChildren = innerDir.children
    assertEquals(5, innerChildren.size)
    assertEquals(log4jModuleLibrary, (innerChildren[0] as LibraryFilesPackagingElementEntity).library.resolve(storage))
    assertEquals(junitProjectLibrary, (innerChildren[1] as LibraryFilesPackagingElementEntity).library.resolve(storage))
    assertEquals(File(projectDir, "main.iml").absolutePath, JpsPathUtil.urlToOsPath((innerChildren[2] as FileCopyPackagingElementEntity).file.url))
    assertEquals(File(projectDir, "lib/junit-anno").absolutePath, JpsPathUtil.urlToOsPath((innerChildren[3] as DirectoryCopyPackagingElementEntity).directory.url))
    (innerChildren[4] as ExtractedDirectoryPackagingElementEntity).let {
      assertEquals(File(projectDir, "lib/junit.jar").absolutePath, JpsPathUtil.urlToOsPath(it.archive.url))
      assertEquals("/junit/", it.pathInArchive)
    }

    assertEquals("jar", artifacts[1].name)
    assertEquals(File(projectDir, "out/artifacts/jar").absolutePath, JpsPathUtil.urlToOsPath(artifacts[1].outputUrl.url))
    val archiveRoot = artifacts[1].rootElement as ArchivePackagingElementEntity
    assertEquals("jar.jar", archiveRoot.fileName)
    val archiveChildren = archiveRoot.children
    assertEquals(3, archiveChildren.size)
    assertEquals(artifacts[0], (archiveChildren[2] as ArtifactOutputPackagingElementEntity).artifact.resolve(storage))
  }

  @Test
  fun `test custom packaging elements`() {
    val projectDir = File(PathManager.getHomePath(), "plugins/javaee/core/javaee-jps-plugin/testData/javaeeSampleProject/javaeeSampleProject.ipr")
    val storage = loadProject(projectDir)
    val artifacts = storage.entities(ArtifactEntity::class).toList()
    assertEquals(6, artifacts.size)
    assertEquals("javaeeSampleProject:war exploded", artifacts[0].name)
    val artifactChildren = artifacts[0].rootElement.children
    assertEquals(2, artifactChildren.size)
    val customElement = artifactChildren[0] as CustomPackagingElementEntity
    assertEquals("javaee-facet-resources", customElement.typeId)
    assertEquals("<element id=\"javaee-facet-resources\" facet=\"javaeeSampleProject/web/Web\" />", customElement.propertiesXmlTag)
  }

  fun `test custom source root`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel-ide/testData/serialization/customSourceRoot/customSourceRoot.ipr")
    val storage = loadProject(projectDir)
    val module = assertOneElement(storage.entities(ModuleEntity::class).toList())
    val sourceRoot = assertOneElement(module.sourceRoots.toList())
    val url = sourceRoot.url.url
    assertEquals("erlang-include", sourceRoot.rootType)
    assertEquals("<sourceFolder url=\"$url\" type=\"erlang-include\" />", sourceRoot.asCustomSourceRoot()?.propertiesXmlTag)
  }

  private fun loadProject(projectFile: File): TypedEntityStorage {
    val storageBuilder = TypedEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadProject(projectFile.asStoragePlace(), storageBuilder)
    return storageBuilder.toStorage()
  }
}
