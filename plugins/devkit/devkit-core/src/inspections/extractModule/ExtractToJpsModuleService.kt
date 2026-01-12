// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModulePackageIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

@Service(Service.Level.PROJECT)
internal class ExtractToJpsModuleService(private val project: Project, private val coroutineScope: CoroutineScope) {
  fun extractToContentModule(problemDescriptor: ProblemDescriptor) {
    coroutineScope.launch {
      val originalData = readAction {
        computeOriginalData(problemDescriptor)
      } ?: return@launch
      val actualData = withContext(Dispatchers.EDT) {
        ExtractToJpsModuleDialog(originalData).showAndGetResult()
      } ?: return@launch
      runExtraction(actualData)
    }
  }

  private suspend fun runExtraction(data: ExtractToContentModuleData) {
    val filesThatDependOnDescriptor = readAction {
      PluginIdDependenciesIndex.findDescriptorsWithReferenceInDependenciesTag(
        GlobalSearchScopesCore.projectProductionScope(project),
        data.originalContentModuleName,
      )
    }

    createModule(data)

    val resourcesDirectoryPath = withContext(Dispatchers.IO) {
      Path(data.newModuleDirectoryPath).resolve(RESOURCES_DIR_NAME).createDirectories()
    }
    writeAction {
      val resourcesDirectory = LocalFileSystem.getInstance().refreshAndFindFileByPath(resourcesDirectoryPath.toString())!!
      data.descriptor.move(this, resourcesDirectory)
      if (data.descriptor.nameWithoutExtension != data.newModuleName) {
        data.descriptor.rename(this, "${data.newModuleName}.xml")
      }
      executeCommand(project = project) {
        updateReferenceInPluginXml(data.pluginXmlFile, data.originalContentModuleName, data.newModuleName)
        updateReferencesInDependenciesTags(filesThatDependOnDescriptor, data.originalContentModuleName, data.newModuleName)
      }
    }
  }

  private fun updateReferencesInDependenciesTags(
    filesThatDependOnDescriptor: Collection<VirtualFile>,
    originalContentModuleName: String,
    newModuleName: String,
  ) {
    for (file in filesThatDependOnDescriptor) {
      val psiFile = file.findPsiFile(project) as? XmlFile ?: continue
      val domElement = DomManager.getDomManager(project).getFileElement(psiFile, IdeaPlugin::class.java)?.rootElement ?: continue
      val dependencyDescriptor = domElement.dependencies.moduleEntry.find { it.name.stringValue == originalContentModuleName }
      if (dependencyDescriptor != null) {
        dependencyDescriptor.name.stringValue = newModuleName
      }
    }
  }

  private fun updateReferenceInPluginXml(pluginXmlFile: VirtualFile, originalModuleName: String, newModuleName: String) {
    val psiFile = pluginXmlFile.findPsiFile(project) as? XmlFile ?: return
    val domElement = DomManager.getDomManager(project).getFileElement(psiFile, IdeaPlugin::class.java)?.rootElement ?: return
    val moduleDescriptor =
      domElement.content.asSequence().flatMap { it.moduleEntry.asSequence() }
        .find { it.name.stringValue == originalModuleName }
    if (moduleDescriptor != null) {
      moduleDescriptor.name.stringValue = newModuleName
    }
  }

  private suspend fun createModule(data: ExtractToContentModuleData) {
    project.workspaceModel.update("Extract JPS module from content module") { builder ->
      val moduleDir = project.workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl(VfsUtil.pathToUrl(data.newModuleDirectoryPath))
      val entitySource = LegacyBridgeJpsEntitySourceFactory.getInstance(project)
        .createEntitySourceForModule(baseModuleDir = moduleDir, externalSource = null)
      builder.addEntity(ModuleEntity(
        name = data.newModuleName,
        dependencies = listOf(InheritedSdkDependency, ModuleSourceDependency),
        entitySource = entitySource
      ) {
        contentRoots = listOf(ContentRootEntity(
          url = moduleDir,
          excludedPatterns = emptyList(),
          entitySource = entitySource,
        ) {
          sourceRoots = listOfNotNull(
            SourceRootEntity(
              url = moduleDir.append(RESOURCES_DIR_NAME),
              rootTypeId = JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID,
              entitySource = entitySource,
            ),
            if (data.packageName != null) {
              SourceRootEntity(
                url = moduleDir.append("src"),
                rootTypeId = JAVA_SOURCE_ROOT_ENTITY_TYPE_ID,
                entitySource = entitySource,
              ) {
                javaSourceRoots = listOf(
                  JavaSourceRootPropertiesEntity(
                    entitySource = entitySource,
                    generated = false,
                    packagePrefix = data.packageName,
                  )
                )
              }
            }
            else null
          )
        })
      })
    }
  }

  private fun computeOriginalData(problemDescriptor: ProblemDescriptor): ExtractToContentModuleData? {
    val xmlElement = problemDescriptor.psiElement as? XmlTag ?: return null
    val domElement = DomManager.getDomManager(project).getDomElement(xmlElement) as? ContentDescriptor.ModuleDescriptor ?: return null
    val originalContentModuleName = domElement.name.stringValue ?: return null
    val resolvedModuleDescriptor = domElement.name.value ?: return null
    val pluginXmlFile = xmlElement.containingFile.virtualFile ?: return null
    val descriptor = resolvedModuleDescriptor.xmlElement?.containingFile?.virtualFile ?: return null
    val originalModule = ModuleUtilCore.findModuleForFile(descriptor, project) ?: return null
    val contentRoot = ModuleRootManager.getInstance(originalModule).contentRoots.firstOrNull() ?: return null

    val packageName = resolvedModuleDescriptor.`package`.stringValue
    val packageDirectory =
      if (packageName != null) ModulePackageIndex.getInstance(originalModule).getDirectoriesByPackageName(packageName, false).firstOrNull()
      else null
    val newModuleName =
      if (packageDirectory != null) packageName!!.removePrefix("com.")
      else descriptor.nameWithoutExtension
    val newModuleDirectoryPath = contentRoot.path + "/" + newModuleName.removePrefix(originalModule.name + ".")
    return ExtractToContentModuleData(
      descriptor = descriptor,
      originalContentModuleName = originalContentModuleName,
      pluginXmlFile = pluginXmlFile,
      originalModule = originalModule,
      newModuleName = newModuleName,
      newModuleDirectoryPath = newModuleDirectoryPath,
      packageName = packageName?.takeIf { packageDirectory != null },
      packageDirectory = packageDirectory
    )
  }
}

private const val RESOURCES_DIR_NAME = "resources"

internal data class ExtractToContentModuleData(
  val descriptor: VirtualFile,
  val originalContentModuleName: @NlsSafe String,
  val pluginXmlFile: VirtualFile,
  val originalModule: Module,
  val newModuleName: String,
  val newModuleDirectoryPath: String,
  val packageName: String?,
  val packageDirectory: VirtualFile?,
)
