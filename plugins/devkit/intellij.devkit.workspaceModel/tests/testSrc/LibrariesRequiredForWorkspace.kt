// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.android.ide.common.repository.AgpVersion
import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library.ModifiableModel
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.PathUtil
import com.jetbrains.rd.framework.RdId
import com.jetbrains.rd.util.string.IPrintable
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase.assertNotNull
import kotlin.script.experimental.api.SourceCode

internal object LibrariesRequiredForWorkspace {
  val workspaceStorage = ModuleLibrary("intellij.platform.workspace.storage")
  val workspaceJpsEntities = ModuleLibrary("intellij.platform.workspace.jps")
  private val intellijJava = ModuleLibrary("intellij.java")

  private val rider = ModuleLibrary("intellij.rider")
  private val riderUnityPlugin = ModuleLibrary("intellij.rider.plugins.unity")
  private val riderModelGenerated = ModuleLibrary("intellij.rider.model.generated")
  private val rdIdeModelGenerated = ModuleLibrary("intellij.rd.ide.model.generated")
  private val riderRdClient = ModuleLibrary("intellij.rider.rdclient.dotnet")
  private val gradle = ModuleLibrary("intellij.gradle")
  private val gradleToolingExtension = ModuleLibrary("intellij.gradle.toolingExtension")
  private val gradleExternalSystemImpl = ModuleLibrary("intellij.platform.externalSystem.impl")
  private val pyCommon = ModuleLibrary("intellij.python.common")
  private val cidrProjectModel = ModuleLibrary("intellij.cidr.projectModel")
  private val kotlinBaseScripting = ModuleLibrary("intellij.kotlin.base.scripting")
  private val androidProjectSystem = ModuleLibrary("intellij.android.projectSystem")
  private val androidGradleModels = ModuleLibrary("intellij.android.projectSystem.gradle.models")

  private val kotlinJpsCommon = JarLibrary("kotlinc-kotlin-jps-common", KotlinModuleKind::class.java)
  private val kotlinScriptingCommon = JarLibrary("kotlinc-kotlin-scripting-common", SourceCode::class.java)
  private val rdCore = JarLibrary("rd-core", IPrintable::class.java)
  private val rdFramework = JarLibrary("rd-framework", RdId::class.java)
  private val androidStudioPlatform = JarLibrary("studio-platform", AgpVersion::class.java)

  fun getRelatedLibraries(moduleEntityName: String): List<RelatedLibrary> =
    when (moduleEntityName) {
      "intellij.javaee.platform", "intellij.javaee.ejb", "intellij.javaee.web", "intellij.amper", "intellij.java.impl" -> {
        listOf(intellijJava)
      }
      "intellij.rider.plugins.unity" -> {
        listOf(riderUnityPlugin, rdCore)
      }
      "intellij.rider" -> {
        listOf(riderRdClient)
      }
      "intellij.rider.rdclient.dotnet" -> {
        listOf(rdFramework, rdCore, riderModelGenerated, riderUnityPlugin, rider, rdIdeModelGenerated)
      }
      "intellij.kotlin.base.facet" -> {
        listOf(intellijJava, kotlinJpsCommon)
      }
      "intellij.kotlin.base.scripting" -> {
        listOf(kotlinScriptingCommon)
      }
      "intellij.kotlin.gradle.scripting" -> {
        listOf(kotlinBaseScripting)
      }
      "intellij.gradle" -> {
        listOf(gradleToolingExtension, gradleExternalSystemImpl)
      }
      "intellij.gradle.tests" -> {
        listOf(gradle, gradleToolingExtension)
      }
      "intellij.python.pyproject" -> {
        listOf(pyCommon)
      }
      "intellij.clion.openfolder" -> {
        listOf(cidrProjectModel)
      }
      "intellij.android.projectSystem.gradle" -> {
        listOf(androidProjectSystem, androidGradleModels, androidStudioPlatform)
      }
      else -> {
        emptyList()
      }
    }
}

internal interface RelatedLibrary {
  fun add(model: ModifiableRootModel)

  fun remove(model: ModifiableRootModel)
}

internal class ModuleLibrary(private val classpath: String) : RelatedLibrary {
  private val name = classpath.replace(".", "-")

  override fun add(model: ModifiableRootModel) {
    addLibraryBaseOnPath(model, name, classpath)
  }

  override fun remove(model: ModifiableRootModel) {
    removeLibraryByName(model, name)
  }
}

internal class JarLibrary(private val name: String, private val libraryClass: Class<*>) : RelatedLibrary {
  override fun add(model: ModifiableRootModel) {
    addJarDirectoryBaseOnClass(model, name, libraryClass)
  }

  override fun remove(model: ModifiableRootModel) {
    removeLibraryByName(model, name)
  }
}

private fun removeLibraryByName(model: ModifiableRootModel, libraryName: String) {
  val moduleLibraryTable = model.moduleLibraryTable
  val modifiableModel = model.moduleLibraryTable.modifiableModel
  val library = moduleLibraryTable.libraries.find { it.name == libraryName } ?: error("Library $libraryName has to be available")
  modifiableModel.removeLibrary(library)
  modifiableModel.commit()
}

private fun addLibraryBaseOnPath(model: ModifiableRootModel, libraryName: String, classpath: String) {
  addDependencyFromCompilationOutput(model, libraryName, classpath) {
    addRoot(it, OrderRootType.CLASSES)
  }
}

private fun addJarDirectoryBaseOnClass(model: ModifiableRootModel, libraryName: String, baseClass: Class<*>) {
  addDependencyFromCompilationOutput(model, libraryName, baseClass)
}

private fun getSharedClassesRootVirtualFile(): VirtualFile {
  val classesPathUrl = VfsUtil.pathToUrl(PathUtil.getJarPathForClass(WorkspaceEntity::class.java))
  val classesRootVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(classesPathUrl)
  val sharedClassesRootVirtualFile = classesRootVirtualFile?.parent
  assertNotNull("Cannot find $sharedClassesRootVirtualFile. Possibly, project was not compiled", sharedClassesRootVirtualFile)
  return sharedClassesRootVirtualFile!!
}

internal fun refreshCompilationOutputInVfs() {
  val sharedClassesRootVirtualFile = getSharedClassesRootVirtualFile()
  VfsUtil.markDirtyAndRefresh(false, true, true, sharedClassesRootVirtualFile)
  // markDirtyAndRefresh above is not enough, it does not add "new" files to the VFS
  VfsUtil.iterateChildrenRecursively(sharedClassesRootVirtualFile!!, null) {
    true
  }
}

private fun addDependencyFromCompilationOutput(model: ModifiableRootModel, libraryName: String, baseClass: Class<*>) {
  val library = model.moduleLibraryTable.modifiableModel.createLibrary(libraryName)
  val modifiableModel = library.modifiableModel
  val classesPathUrl = VfsUtil.pathToUrl(PathUtil.getJarPathForClass(baseClass))
  var classesRootVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(classesPathUrl)
  assertNotNull("Cannot find $classesPathUrl", classesRootVirtualFile)

  if (classesRootVirtualFile!!.isFile && classesRootVirtualFile.extension == "jar") {
    classesRootVirtualFile = JarFileSystem.getInstance().getJarRootForLocalFile(classesRootVirtualFile)
    assertNotNull("Cannot convert $classesPathUrl to a jar VirtualFile", classesRootVirtualFile)
    VfsUtil.markDirtyAndRefresh(false, true, true, classesRootVirtualFile)
    modifiableModel.addRoot(classesRootVirtualFile!!, OrderRootType.CLASSES)
  }
  else {
    VfsUtil.markDirtyAndRefresh(false, true, true, classesRootVirtualFile)
    modifiableModel.addJarDirectory(classesRootVirtualFile, true)
  }
  modifiableModel.commit()
}

private fun addDependencyFromCompilationOutput(model: ModifiableRootModel, libraryName: String, classpathFolder: String, addDependency: ModifiableModel.(VirtualFile) -> Unit) {
  val library = model.moduleLibraryTable.modifiableModel.createLibrary(libraryName)
  val modifiableModel = library.modifiableModel

  var classpathRootVirtualFile: VirtualFile?

  val mapping = ArchivedCompilationContextUtil.archivedCompiledClassesMapping
  if (mapping != null) {
    val jar = mapping["production/$classpathFolder"] ?: error("No jar found for $classpathFolder production classes")
    classpathRootVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.pathToUrl(jar))
    assertNotNull("Cannot find $classpathFolder in production classes jars. Possibly, project was partially compiled", classpathRootVirtualFile)
  }
  else {
    val sharedClassesRootVirtualFile = getSharedClassesRootVirtualFile()
    classpathRootVirtualFile = sharedClassesRootVirtualFile.children?.find { it.name == classpathFolder }
    assertNotNull("Cannot find $classpathFolder in $sharedClassesRootVirtualFile. Possibly, project was partially compiled", classpathRootVirtualFile)
  }
  VfsUtil.markDirtyAndRefresh(false, true, true, classpathRootVirtualFile)

  if (classpathRootVirtualFile!!.isFile && classpathRootVirtualFile.extension == "jar") {
    val file = classpathRootVirtualFile
    classpathRootVirtualFile = JarFileSystem.getInstance().getJarRootForLocalFile(file)
    assertNotNull("Cannot convert $file to a jar VirtualFile", classpathRootVirtualFile)
    VfsUtil.markDirtyAndRefresh(false, true, true, classpathRootVirtualFile)
  }

  modifiableModel.addDependency(classpathRootVirtualFile!!)
  modifiableModel.commit()
}