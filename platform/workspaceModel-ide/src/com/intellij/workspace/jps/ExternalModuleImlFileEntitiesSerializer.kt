// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.module.impl.ModulePath
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.JpsImportedEntitySource
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.util.JpsPathUtil

internal class ExternalModuleImlFileEntitiesSerializer(modulePath: ModulePath,
                                                       fileUrl: VirtualFileUrl,
                                                       internalEntitySource: JpsFileEntitySource)
  : ModuleImlFileEntitiesSerializer(modulePath, fileUrl, internalEntitySource) {

  override fun acceptsSource(entitySource: EntitySource): Boolean {
    return entitySource is JpsImportedEntitySource && entitySource.storedExternally
  }

  override fun readExternalSystemOptions(reader: JpsFileContentReader,
                                         moduleOptions: Map<String?, String?>): Pair<Map<String?, String?>, String?> {
    val componentTag = reader.loadComponent(fileUrl.url, "ExternalSystem") ?: return Pair(emptyMap(), null)
    val options = componentTag.attributes.associateBy({ it.name }, { it.value })
    return Pair(options, options["externalSystem"])
  }

  override fun loadExternalSystemOptions(builder: TypedEntityStorageBuilder,
                                         module: ModuleEntity,
                                         reader: JpsFileContentReader,
                                         externalSystemOptions: Map<String?, String?>,
                                         externalSystemId: String?,
                                         entitySource: EntitySource) {
    val optionsEntity = builder.getOrCreateExternalSystemModuleOptions(module, entitySource)
    builder.modifyEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, optionsEntity) {
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
    if (externalSystemOptions != null) {
      val componentTag = JDomSerializationUtil.createComponentElement("ExternalSystem")

      fun saveOption(name: String, value: String?) {
        if (value != null) {
          componentTag.setAttribute(name, value)
        }
      }
      saveOption("externalSystem", externalSystemOptions.externalSystem)
      saveOption("externalSystemModuleVersion", externalSystemOptions.externalSystemModuleVersion)
      saveOption("externalSystemModuleGroup", externalSystemOptions.externalSystemModuleGroup)
      saveOption("externalSystemModuleType", externalSystemOptions.externalSystemModuleType)
      saveOption("linkedProjectPath", externalSystemOptions.linkedProjectPath)
      saveOption("linkedProjectId", externalSystemOptions.linkedProjectId)
      saveOption("rootProjectPath", externalSystemOptions.rootProjectPath)
      writer.saveComponent(fileUrl.url, "ExternalSystem", componentTag)
    }
    if (moduleType != null) {
      val componentTag = JDomSerializationUtil.createComponentElement("DeprecatedModuleOptionManager")
      componentTag.addContent(Element("option").setAttribute("key", "type").setAttribute("value", moduleType))
      writer.saveComponent(fileUrl.url, "DeprecatedModuleOptionManager", componentTag)
    }
  }

  override fun createExternalEntitySource(externalSystemId: String) =
    JpsImportedEntitySource(internalEntitySource, externalSystemId, true)

  override fun createFacetSerializer(): FacetEntitiesSerializer {
    return FacetEntitiesSerializer(fileUrl, internalEntitySource, "ExternalFacetManager", true)
  }
}

internal class ExternalModuleSerializersFactory(externalStorageRoot: VirtualFileUrl) :
  ModuleSerializersFactory(externalStorageRoot.append("project/modules.xml").url) {
  override val componentName: String
    get() = "ExternalProjectModuleManager"

  override val entitySourceFilter: (EntitySource) -> Boolean
    get() = { it is JpsImportedEntitySource && it.storedExternally }

  override fun getSourceToSave(module: ModuleEntity): JpsFileEntitySource.FileInDirectory? {
    return (module.entitySource as? JpsImportedEntitySource)?.internalFile as? JpsFileEntitySource.FileInDirectory
  }

  override fun getFileName(entity: ModuleEntity): String {
    return "${entity.name}.xml"
  }

  override fun createSerializer(internalSource: JpsFileEntitySource, fileUrl: VirtualFileUrl): JpsFileEntitiesSerializer<ModuleEntity> {
    return ExternalModuleImlFileEntitiesSerializer(ModulePath(JpsPathUtil.urlToPath(fileUrl.filePath), null), fileUrl,
                                                   internalSource)
  }
}