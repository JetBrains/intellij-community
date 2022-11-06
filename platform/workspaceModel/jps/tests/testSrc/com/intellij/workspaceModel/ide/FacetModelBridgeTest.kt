// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.facet.FacetManager
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.mock.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.ModifiableFacetModelBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.toBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.AssertionFailedError
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class FacetModelBridgeTest {
  companion object {
    @ClassRule
    @JvmField
    val application = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private val virtualFileManager: VirtualFileUrlManager
    get() = VirtualFileUrlManager.getInstance(projectModel.project)

  @Before
  fun registerFacetType() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposableRule.disposable) {}
    registerFacetType(MockFacetType(), disposableRule.disposable)
    registerFacetType(AnotherMockFacetType(), disposableRule.disposable)
  }

  @Test
  fun `test changed facet config saved correctly`() {
    val facetData = "mock"
    val module = projectModel.createModule()
    val facet = MockFacet(module, facetData)
    val facetManager = FacetManager.getInstance(module)
    facetManager.createModifiableModel().let { modifiableModel ->
      modifiableModel.addFacet(facet)
      assertTrue(facet.configuration.data.isEmpty())
      facet.configuration.data = facetData
      runWriteActionAndWait { modifiableModel.commit() }
    }
    val facetConfigXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }

    val facetByType = facetManager.getFacetByType(MockFacetType.ID)
    assertNotNull(facetByType)
    assertEquals(facetData, facetByType!!.configuration.data)

    val entityStorage = WorkspaceModel.getInstance(projectModel.project).entityStorage
    val facetEntity = entityStorage.current.entities(FacetEntity::class.java).first()
    assertEquals(facetConfigXml, facetEntity.configurationXmlTag)

    facetManager.createModifiableModel().let { modifiableModel ->
      modifiableModel as ModifiableFacetModelBridgeImpl
      assertEquals(facetConfigXml, modifiableModel.getEntity(facet)!!.configurationXmlTag)
    }
  }

  @Test
  fun `facet with caching`() {
    val builder = MutableEntityStorage.create()

    val baseDir = projectModel.baseProjectDir.rootPath.resolve("test")
    val iprFile = baseDir.resolve("testProject.ipr")
    val configLocation = toConfigLocation(iprFile, virtualFileManager)
    val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)

    val moduleEntity = builder.addModuleEntity(name = "test", dependencies = emptyList(), source = source)

    builder.addFacetEntity("MyFacet", "MockFacetId", """<configuration data="foo" />""", moduleEntity,
                           null, source)

    WorkspaceModelInitialTestContent.withInitialContent(builder.toSnapshot()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile, disposableRule.disposable)
      Disposer.register(disposableRule.disposable, Disposable {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      })

      val module = ModuleManager.getInstance(project).findModuleByName("test") ?: throw AssertionFailedError("Module wasn't loaded")
      val facets = FacetManager.getInstance(module).allFacets
      val facet = assertOneElement(facets) as MockFacet
      assertEquals("MyFacet", facet.name)
      assertEquals("foo", facet.configuration.data)
      assertTrue(facet.isInitialized)
    }
  }

  @Test
  fun `facet config immutable collections deserialization`() {
    val builder = MutableEntityStorage.create()

    val baseDir = projectModel.baseProjectDir.rootPath.resolve("test")
    val iprFile = baseDir.resolve("testProject.ipr")
    val configLocation = toConfigLocation(iprFile, virtualFileManager)
    val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)

    val moduleEntity = builder.addModuleEntity(name = "test", dependencies = emptyList(), source = source)

    builder.addFacetEntity("AnotherMockFacet", "AnotherMockFacetId", """
      <AnotherFacetConfigProperties>
        <firstElement>
          <field>Android</field>
        </firstElement>
        <secondElement>
          <field>Spring</field>
        </secondElement>
      </AnotherFacetConfigProperties>""", moduleEntity, null, source)

    WorkspaceModelInitialTestContent.withInitialContent(builder.toSnapshot()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile, disposableRule.disposable)
      Disposer.register(disposableRule.disposable, Disposable {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      })

      val module = ModuleManager.getInstance(project).findModuleByName("test") ?: throw AssertionFailedError("Module wasn't loaded")
      val facets = FacetManager.getInstance(module).allFacets
      val facet = assertOneElement(facets) as AnotherMockFacet
      assertEquals("AnotherMockFacet", facet.name)
      val configProperties = facet.configuration.myProperties
      assertEquals("Android", configProperties.firstElement[0])
      assertEquals("Spring", configProperties.secondElement[0])
      assertTrue(facet.isInitialized)
    }
  }

  @Test
  fun `update facet configuration via entity`() {
    val module = projectModel.createModule()
    val facet = projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("foo"))
    assertEquals("foo", facet.configuration.data)
    runWriteActionAndWait {
      WorkspaceModel.getInstance(projectModel.project).updateProjectModel { builder ->
        val facetEntity = builder.entities(FacetEntity::class.java).single()
        builder.modifyEntity(facetEntity) {
          configurationXmlTag = """<configuration data="bar" />"""
        }
      }
    }
    assertEquals("bar", facet.configuration.data)
  }

  @Test
  fun `getting module facets after module rename`() {
    val module = projectModel.createModule()
    val facet = projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("foo"))

    val diff = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.toBuilder()
    val modifiableModuleModel = (ModuleManager.getInstance(projectModel.project) as ModuleManagerBridgeImpl).getModifiableModel(diff)
    val modifiableFacetModel = (FacetManager.getInstance(module) as FacetManagerBridge).createModifiableModel(diff)

    var existingFacet = assertOneElement(modifiableFacetModel.allFacets)
    assertEquals(facet.name, existingFacet.name)
    modifiableModuleModel.renameModule(module, "newModuleName")
    existingFacet = assertOneElement(modifiableFacetModel.allFacets)
    assertEquals(facet.name, existingFacet.name)
  }
}
