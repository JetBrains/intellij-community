// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspaceModel.jps.JpsFileEntitySource
import com.intellij.platform.workspaceModel.jps.JpsImportedEntitySource
import com.intellij.platform.workspaceModel.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspaceModel.jps.serialization.SerializationContext
import com.intellij.platform.workspaceModel.jps.serialization.impl.ModulePath
import com.intellij.util.PathUtilRt
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleCustomImlDataEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.util.JpsPathUtil

private val MODULE_OPTIONS_TO_CHECK = setOf(
  "externalSystemModuleVersion", "linkedProjectPath", "linkedProjectId", "rootProjectPath", "externalSystemModuleGroup",
  "externalSystemModuleType"
)

internal class ExternalModuleImlFileEntitiesSerializer(modulePath: ModulePath,
                                                       fileUrl: VirtualFileUrl,
                                                       context: SerializationContext,
                                                       internalEntitySource: JpsFileEntitySource,
                                                       internalModuleListSerializer: JpsModuleListSerializer)
  : ModuleImlFileEntitiesSerializer(modulePath, fileUrl, internalEntitySource, context, internalModuleListSerializer) {
  override val skipLoadingIfFileDoesNotExist: Boolean
    get() = true

  override fun loadEntities(reader: JpsFileContentReader,
                            errorReporter: ErrorReporter,
                            virtualFileManager: VirtualFileUrlManager): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity>>> {
    return LoadingResult(emptyMap(), null)
  }

  override fun acceptsSource(entitySource: EntitySource): Boolean {
    return entitySource is JpsImportedEntitySource && entitySource.storedExternally
  }

  override fun readExternalSystemOptions(reader: JpsFileContentReader,
                                         moduleOptions: Map<String?, String?>): Pair<Map<String?, String?>, String?> {
    val componentTag = reader.loadComponent(fileUrl.url, "ExternalSystem", getBaseDirPath()) ?: return Pair(emptyMap(), null)
    val options = componentTag.attributes.associateBy({ it.name }, { it.value })
    return Pair(options, options["externalSystem"])
  }

  override fun loadExternalSystemOptions(module: ModuleEntity,
                                         reader: JpsFileContentReader,
                                         externalSystemOptions: Map<String?, String?>,
                                         externalSystemId: String?,
                                         entitySource: EntitySource) {
    if (!shouldCreateExternalSystemModuleOptions(externalSystemId, externalSystemOptions, MODULE_OPTIONS_TO_CHECK)) return
    ExternalSystemModuleOptionsEntity(entitySource) {
      this.module = module
      externalSystem = externalSystemId
      externalSystemModuleVersion = externalSystemOptions["externalSystemModuleVersion"]
      linkedProjectPath = externalSystemOptions["linkedProjectPath"]
      linkedProjectId = externalSystemOptions["linkedProjectId"]
      rootProjectPath = externalSystemOptions["rootProjectPath"]
      externalSystemModuleGroup = externalSystemOptions["externalSystemModuleGroup"]
      externalSystemModuleType = externalSystemOptions["externalSystemModuleType"]
    }
  }

  override fun saveModuleOptions(externalSystemOptions: ExternalSystemModuleOptionsEntity?,
                                 moduleType: String?,
                                 customImlData: ModuleCustomImlDataEntity?,
                                 writer: JpsFileContentWriter) {
    val fileUrlString = fileUrl.url
    if (FileUtil.extensionEquals(fileUrlString, "iml")) {
      logger<ExternalModuleImlFileEntitiesSerializer>().error("External serializer should not write to iml files. Path:$fileUrlString")
    }
    if (externalSystemOptions != null) {
      val componentTag = JDomSerializationUtil.createComponentElement("ExternalSystem")

      fun saveOption(name: String, value: String?) {
        if (value != null) {
          componentTag.setAttribute(name, value)
        }
      }
      saveOption("externalSystem", externalSystemOptions.externalSystem)
      saveOption("externalSystemModuleGroup", externalSystemOptions.externalSystemModuleGroup)
      saveOption("externalSystemModuleType", externalSystemOptions.externalSystemModuleType)
      saveOption("externalSystemModuleVersion", externalSystemOptions.externalSystemModuleVersion)
      saveOption("linkedProjectId", externalSystemOptions.linkedProjectId)
      saveOption("linkedProjectPath", externalSystemOptions.linkedProjectPath)
      saveOption("rootProjectPath", externalSystemOptions.rootProjectPath)
      writer.saveComponent(fileUrlString, "ExternalSystem", componentTag)
    }
    if (moduleType != null || !customImlData?.customModuleOptions.isNullOrEmpty()) {
      val componentTag = JDomSerializationUtil.createComponentElement(DEPRECATED_MODULE_MANAGER_COMPONENT_NAME)
      if (moduleType != null) {
        componentTag.addContent(Element("option").setAttribute("key", "type").setAttribute("value", moduleType))
      }
      customImlData?.customModuleOptions?.forEach{ (key, value) ->
        componentTag.addContent(Element("option").setAttribute("key", key).setAttribute("value", value))
      }
      writer.saveComponent(fileUrlString, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, componentTag)
    }
  }

  override fun createExternalEntitySource(externalSystemId: String) =
    JpsImportedEntitySource(internalEntitySource, externalSystemId, true)

  override fun createFacetSerializer(): FacetsSerializer {
    return FacetsSerializer(fileUrl, internalEntitySource, "ExternalFacetManager", getBaseDirPath(), true, context)
  }

  override fun getBaseDirPath(): String {
    return modulePath.path
  }

  override fun toString(): String = "ExternalModuleImlFileEntitiesSerializer($fileUrl)"
}

internal class ExternalModuleListSerializer(private val externalStorageRoot: VirtualFileUrl,
                                            context: SerializationContext) :
  ModuleListSerializerImpl(externalStorageRoot.append("project/modules.xml").url, context) {
  override val isExternalStorage: Boolean
    get() = true

  override val componentName: String
    get() = "ExternalProjectModuleManager"

  override val entitySourceFilter: (EntitySource) -> Boolean
    get() = { it is JpsImportedEntitySource && it.storedExternally }

  override fun getSourceToSave(module: ModuleEntity): JpsProjectFileEntitySource.FileInDirectory? {
    return (module.entitySource as? JpsImportedEntitySource)?.internalFile as? JpsProjectFileEntitySource.FileInDirectory
  }

  override fun getFileName(entity: ModuleEntity): String {
    return "${entity.name}.xml"
  }

  override fun createSerializer(internalSource: JpsFileEntitySource, fileUrl: VirtualFileUrl, moduleGroup: String?): JpsFileEntitiesSerializer<ModuleEntity> {
    val fileName = PathUtilRt.getFileName(fileUrl.url)
    val actualFileUrl = if (PathUtilRt.getFileExtension(fileName) == "iml") {
      externalStorageRoot.append("modules/${fileName.substringBeforeLast('.')}.xml")
    }
    else {
      fileUrl
    }
    val filePath = JpsPathUtil.urlToPath(fileUrl.url)
    return ExternalModuleImlFileEntitiesSerializer(ModulePath(filePath, moduleGroup), actualFileUrl, context, internalSource, this)
  }

  // Component DeprecatedModuleOptionManager removed by ModuleStateStorageManager.beforeElementSaved from .iml files
  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    super.deleteObsoleteFile(fileUrl, writer)
    if (FileUtil.extensionEquals(fileUrl, "xml")) {
      writer.saveComponent(fileUrl, "ExternalSystem", null)
      writer.saveComponent(fileUrl, "ExternalFacetManager", null)
      writer.saveComponent(fileUrl, DEPRECATED_MODULE_MANAGER_COMPONENT_NAME, null)
      writer.saveComponent(fileUrl, TEST_MODULE_PROPERTIES_COMPONENT_NAME, null)
    }
  }

  override fun toString(): String = "ExternalModuleListSerializer($fileUrl)"
}
