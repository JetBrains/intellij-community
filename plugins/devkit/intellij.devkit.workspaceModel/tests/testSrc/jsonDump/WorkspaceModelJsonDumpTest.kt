// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testNameFixture
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.impl.jsonDump.WorkspaceModelJsonDumpSerializer
import com.intellij.workspaceModel.ide.impl.jsonDump.WorkspaceModelJsonDumpService
import com.intellij.workspaceModel.ide.impl.jsonDump.entityChildReferenceJsonName
import com.intellij.workspaceModel.ide.impl.jsonDump.genericParameterForList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@TestApplication
class WorkspaceModelJsonDumpTest {
  private val testName = testNameFixture()
  private val testFile: Path
    get() = Path.of(PathManagerEx.getCommunityHomePath() + "/plugins/devkit/intellij.devkit.workspaceModel/tests/testData/jsonDump/${testName.get()}.json")

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
    val serializer = WorkspaceModelJsonDumpSerializer()
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
      val moduleEntityJson = serializer.entityAsJson(moduleEntity)

      testModuleEntity(moduleEntity, moduleEntityJson)
    }
  }

  private fun testModuleEntity(moduleEntity: ModuleEntity, moduleEntityJson: JsonObject) {
    testEntityJson(moduleEntity, moduleEntityJson)

    val contentRootEntity = moduleEntity.contentRoots.single()
    val contentRootEntityJsonName = entityChildReferenceJsonName("ContentRootEntity", true)
    val contentRootJson = moduleEntityJson[contentRootEntityJsonName]?.jsonArray?.single() as JsonObject
    testEntityJson(contentRootEntity, contentRootJson)

    val sourceRootEntity = contentRootEntity.sourceRoots.single()
    val sourceRootEntityJsonName = entityChildReferenceJsonName("SourceRootEntity", true)
    val sourceRootJson = contentRootJson[sourceRootEntityJsonName]?.jsonArray?.single() as JsonObject
    testEntityJson(sourceRootEntity, sourceRootJson)
  }

  private fun testEntityJson(entity: WorkspaceEntity, entityJson: JsonObject) {
    val entityData = entity.data
    for ((name) in entityData.getMetadata().properties.filter { it.supported && it.valueType !is ValueTypeMetadata.EntityReference }) {
      assertTrue(entityJson.containsKey(name)) { "Missing property: $name" }
    }

    assertTrue(entityJson.containsKey("fqn")) { "Missing fqn for entity ${entity.getEntityInterface().name}" }
    assertEquals(entityData.getEntityInterface().name, entityJson["fqn"]?.jsonPrimitive?.content) { "Wrong entity name" }
    assertEquals(
      entityData.entitySource::class.qualifiedName,
      entityJson["entitySource"]?.jsonObject?.get("entitySourceFqn")?.jsonPrimitive?.content
    ) { "Wrong entity source" }
  }

  private val WorkspaceEntity.data: WorkspaceEntityData<out WorkspaceEntity>
    get() = (this as WorkspaceEntityBase).getData()

  private val OwnPropertyMetadata.supported: Boolean
    get() {
      if (isComputable) return false
      val valueType = valueType
      if (valueType is ValueTypeMetadata.EntityReference) return false
      if (valueType is ValueTypeMetadata.ParameterizedType) return valueType.genericParameterForList() != null
      return true
    }

  @Test
  fun mockModule() {
    val serializer = WorkspaceModelJsonDumpSerializer()
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
          this.javaSettings = JavaModuleSettingsEntity(
            true,
            true,
            NonPersistentEntitySource
          )
        })
      }
      val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(mockModuleName)) ?: return@timeoutRunBlocking

      val asJson = serializer.entityAsJson(moduleEntity)
      val mockModuleSerializedActual = json.encodeToString(asJson)
      val description = "ModuleEntity with contentRoots (in interface children) and javaSettings (extension children)"
      // assertEqualsToFile(description, testFile.toFile(), mockModuleSerializedActual)
      assertEquals(mockModuleExpected, mockModuleSerializedActual, description)
    }
  }

  @Test
  fun complexTestEntity() {
    timeoutRunBlocking(20.seconds) {
      val project = projectFixture.get()
      project.waitForSmartMode()

      val vfuManager = project.workspaceModel.getVirtualFileUrlManager()
      project.workspaceModel.update {
        val urls = listOf("1.txt", "2.kt", "3.cpp").map { name -> vfuManager.getOrCreateFromUrl("file:///someDir/subDir/$name") }
        val strings = listOf("a", "a", "b", "c", "b")
        it.addEntity(BaseTestEntity(
          "base",
          listOf(ImplClass1("impl1", 1), ImplClass2("impl2", "name"), ImplClass1("impl1", 2)),
          strings,
          strings.toSet(),
          NonPersistentEntitySource
        ) {
          this.singleChild = SingleChild("some data", NonPersistentEntitySource)
          this.children = listOf(ChildEntity("child name", NonPersistentEntitySource))
          this.extensionChildren = listOf(
            ExtensionChildEntity(
              "extension child name",
              urls,
              NonPersistentEntitySource
            ),
            ExtensionChildEntity(
              "another extension child name",
              urls.drop(1),
              NonPersistentEntitySource
            )
          )
        })
      }

      val complexEntity = project.workspaceModel.currentSnapshot.entities<BaseTestEntity>().singleOrNull()
      val complexEntitySerializedActual = if (complexEntity == null) "Entity not found in the snapshot"
      else json.encodeToString(WorkspaceModelJsonDumpSerializer().entityAsJson(complexEntity))
      val description = "Entity with extension children and list of sealed abstract class property"
      // assertEqualsToFile(description, testFile.toFile(), complexEntitySerializedActual)
      assertEquals(complexEntityExpected, complexEntitySerializedActual, description) 
    }
  }
}

private const val complexEntityExpected = """{
  "fqn": "com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity",
  "entitySource": {
    "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
    "virtualFileUrl": null
  },
  "name": "base",
  "listOfAbstract_Count": 3,
  "listOfAbstract": [
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ImplClass1",
      "string": "impl1",
      "version": "1"
    },
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ImplClass2",
      "name": "name",
      "string": "impl2"
    },
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ImplClass1",
      "string": "impl1",
      "version": "2"
    }
  ],
  "stringList_Count": 5,
  "stringList": [
    "a",
    "a",
    "b",
    "c",
    "b"
  ],
  "stringSet_Count": 3,
  "stringSet": [
    "a",
    "b",
    "c"
  ],
  "Children_ChildEntity_Count": 1,
  "Children_ChildEntity": [
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ChildEntity",
      "entitySource": {
        "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
        "virtualFileUrl": null
      },
      "childName": "child name"
    }
  ],
  "Child_SingleChild": {
    "fqn": "com.intellij.devkit.workspaceModel.jsonDump.SingleChild",
    "entitySource": {
      "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
      "virtualFileUrl": null
    },
    "someData": "some data"
  },
  "Children_ExtensionChildEntity_Count": 2,
  "Children_ExtensionChildEntity": [
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity",
      "entitySource": {
        "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
        "virtualFileUrl": null
      },
      "extensionChildName": "extension child name",
      "listOfUrls_Count": 3,
      "listOfUrls": [
        {
          "url": "file:///someDir/subDir/1.txt"
        },
        {
          "url": "file:///someDir/subDir/2.kt"
        },
        {
          "url": "file:///someDir/subDir/3.cpp"
        }
      ]
    },
    {
      "fqn": "com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity",
      "entitySource": {
        "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
        "virtualFileUrl": null
      },
      "extensionChildName": "another extension child name",
      "listOfUrls_Count": 2,
      "listOfUrls": [
        {
          "url": "file:///someDir/subDir/2.kt"
        },
        {
          "url": "file:///someDir/subDir/3.cpp"
        }
      ]
    }
  ]
}"""

private const val mockModuleExpected = """{
  "fqn": "com.intellij.platform.workspace.jps.entities.ModuleEntity",
  "entitySource": {
    "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
    "virtualFileUrl": null
  },
  "name": "mock module entity",
  "type": "null",
  "dependencies_Count": 3,
  "dependencies": [
    {
      "fqn": "com.intellij.platform.workspace.jps.entities.InheritedSdkDependency"
    },
    {
      "fqn": "com.intellij.platform.workspace.jps.entities.ModuleSourceDependency"
    },
    {
      "fqn": "com.intellij.platform.workspace.jps.entities.ModuleDependency",
      "exported": "false",
      "module": {
        "Unknown \"FinalClassMetadata.KnownClass\"": "com.intellij.platform.workspace.jps.entities.ModuleId"
      },
      "productionOnTest": "true",
      "scope": {
        "Unknown \"FinalClassMetadata.KnownClass\"": "com.intellij.platform.workspace.jps.entities.DependencyScope"
      }
    }
  ],
  "Children_ContentRootEntity_Count": 1,
  "Children_ContentRootEntity": [
    {
      "fqn": "com.intellij.platform.workspace.jps.entities.ContentRootEntity",
      "entitySource": {
        "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
        "virtualFileUrl": null
      },
      "url": {
        "url": "file:///tmp"
      },
      "excludedPatterns_Count": 1,
      "excludedPatterns": [
        "excludedPattern"
      ],
      "Children_SourceRootEntity_Count": 0,
      "Children_SourceRootEntity": []
    }
  ],
  "Child_JavaModuleSettingsEntity": {
    "fqn": "com.intellij.java.workspace.entities.JavaModuleSettingsEntity",
    "entitySource": {
      "entitySourceFqn": "com.intellij.workspaceModel.ide.NonPersistentEntitySource",
      "virtualFileUrl": null
    },
    "inheritedCompilerOutput": "true",
    "excludeOutput": "true",
    "compilerOutput": "null",
    "compilerOutputForTests": "null",
    "languageLevelId": "null",
    "manifestAttributes": "Serializing Map is not supported"
  }
}"""
