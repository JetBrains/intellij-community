// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.openapi.components.service
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.impl.jsonDump.WorkspaceModelJsonDumpService
import com.intellij.workspaceModel.ide.impl.jsonDump.WorkspaceModelSerializers
import com.intellij.workspaceModel.ide.impl.jsonDump.genericParameterForList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class WorkspaceModelJsonDumpTest {
  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    prettyPrint = true
    explicitNulls = true
    prettyPrintIndent = "  "
  }

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture()
  private val rootFixture = moduleFixture.sourceRootFixture()

  @Test
  fun `json dump workspace model using service`() {
    timeoutRunBlocking(20.seconds) {
      val project = projectFixture.get()
      project.waitForSmartMode()
      rootFixture.get()

      val wsmJson = project.service<WorkspaceModelJsonDumpService>().getWorkspaceEntitiesAsJsonArray().single() as JsonObject
      assertTrue(wsmJson.containsKey("rootEntityName"))
      assertEquals(ModuleEntity::class.simpleName, wsmJson["rootEntityName"]?.jsonPrimitive?.content)
      assertTrue(wsmJson.containsKey("entities"))
      val entitiesJson = wsmJson["entities"]?.jsonArray!!
      assertEquals(1, entitiesJson.size)

      val moduleEntity = project.workspaceModel.currentSnapshot.entities<ModuleEntity>().single()
      testModuleEntity(moduleEntity, entitiesJson.single().jsonObject)
    }
  }

  @Test
  fun `json dump a module entity with content and source roots`() {
    val serializers = WorkspaceModelSerializers()
    timeoutRunBlocking {
      val project = projectFixture.get()
      project.waitForSmartMode()
      rootFixture.get()

      val oldModuleEntity = project.workspaceModel.currentSnapshot.entities<ModuleEntity>().single()
      project.workspaceModel.update {
        it.modifyModuleEntity(oldModuleEntity) {
          this.dependencies.add(InheritedSdkDependency)
        }
      }
      val moduleEntity = project.workspaceModel.currentSnapshot.entities<ModuleEntity>().single()
      val moduleEntityJson = json.encodeToJsonElement(serializers[moduleEntity], moduleEntity) as JsonObject

      testModuleEntity(moduleEntity, moduleEntityJson)
    }
  }

  private fun testModuleEntity(moduleEntity: ModuleEntity, moduleEntityJson: JsonObject) {
    testEntityJson(moduleEntity, moduleEntityJson)

    val contentRootEntity = moduleEntity.contentRoots.single()
    val contentRootJson = moduleEntityJson["contentRoots"]?.jsonArray?.single() as JsonObject
    testEntityJson(contentRootEntity, contentRootJson)

    val sourceRootEntity = contentRootEntity.sourceRoots.single()
    val sourceRootJson = contentRootJson["sourceRoots"]?.jsonArray?.single() as JsonObject
    testEntityJson(sourceRootEntity, sourceRootJson)
  }

  private fun testEntityJson(entity: WorkspaceEntity, entityJson: JsonObject) {
    val entityData = entity.data
    for (property in entityData.getMetadata().properties.filter { it.supported }) {
      assertTrue(entityJson.containsKey(property.name)) { "Missing property: ${property.name}" }
    }

    assertTrue(entityJson.containsKey("fqName")) { "Missing fqName for entity ${entity.getEntityInterface().name}" }
    assertEquals(entityData.getEntityInterface().name, entityJson["fqName"]?.jsonPrimitive?.content) { "Wrong entity name" }
    assertEquals(
      entityData.entitySource::class.qualifiedName,
      entityJson["entitySource"]?.jsonObject?.get("entitySourceFqName")?.jsonPrimitive?.content
    ) { "Wrong entity source" }
  }

  private val WorkspaceEntity.data: WorkspaceEntityData<out WorkspaceEntity>
    get() = (this as WorkspaceEntityBase).getData()

  private val OwnPropertyMetadata.supported: Boolean
    get() {
      if (isComputable) return false
      val valueType = valueType
      if (valueType is ValueTypeMetadata.EntityReference) return valueType.isChild
      // TODO: missing Map, see TODO in com.intellij.devkit.workspaceModel.jsonDump.WorkspaceModelSerializers
      if (valueType is ValueTypeMetadata.ParameterizedType) return valueType.genericParameterForList() != null
      return true
    }

  @Test
  fun `json dump mock module entity`() {
    // TODO: dependencies do not work properly (Unknown Known class)
    // see TODO in com.intellij.devkit.workspaceModel.jsonDump.WorkspaceModelSerializers
    val serializers = WorkspaceModelSerializers()
    timeoutRunBlocking {
      val mockModuleName = "mock module entity"

      val project = projectFixture.get()
      project.waitForSmartMode()
      val vfuManager = project.workspaceModel.getVirtualFileUrlManager()
      project.workspaceModel.update {
        val vfu = vfuManager.getOrCreateFromUrl("file:///tmp")
        val moduleDependency = ModuleDependency(ModuleId("no module"), false, DependencyScope.COMPILE, true)
        it.addEntity(ModuleEntity(
          mockModuleName,
          listOf(InheritedSdkDependency, ModuleSourceDependency, moduleDependency),
          NonPersistentEntitySource
        ) {
          this.contentRoots = listOf(ContentRootEntity(
            vfu,
            listOf("excludedPattern"),
            NonPersistentEntitySource
          ))
        })

      }
      val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(mockModuleName)) ?: return@timeoutRunBlocking

      val mockModuleSerializedActual = json.encodeToString(serializers[moduleEntity], moduleEntity)
      val mockModuleSerializedExpected = """
        {
          "fqName": "com.intellij.platform.workspace.jps.entities.ModuleEntity",
          "entitySource": {
            "entitySourceFqName": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
            "virtualFileUrl": null
          },
          "name": "mock module entity",
          "type": null,
          "dependenciesCount": 3,
          "dependencies": [
            {
              "fqName": "com.intellij.platform.workspace.jps.entities.InheritedSdkDependency"
            },
            {
              "fqName": "com.intellij.platform.workspace.jps.entities.ModuleSourceDependency"
            },
            {
              "exported": false,
              "module": "Unknown \"FinalClassMetadata.KnownClass\": com.intellij.platform.workspace.jps.entities.ModuleId",
              "productionOnTest": true,
              "scope": "Unknown \"FinalClassMetadata.KnownClass\": com.intellij.platform.workspace.jps.entities.DependencyScope"
            }
          ],
          "contentRootsCount": 1,
          "contentRoots": [
            {
              "fqName": "com.intellij.platform.workspace.jps.entities.ContentRootEntity",
              "entitySource": {
                "entitySourceFqName": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
                "virtualFileUrl": null
              },
              "url": "file:///tmp",
              "excludedPatternsCount": 1,
              "excludedPatterns": [
                "excludedPattern"
              ],
              "sourceRootsCount": 0,
              "sourceRoots": [],
              "excludedUrlsCount": 0,
              "excludedUrls": []
            }
          ],
          "facetsCount": 0,
          "facets": []
        }
      """.trimIndent()
      assertEquals(mockModuleSerializedExpected, mockModuleSerializedActual)
    }
  }
}
