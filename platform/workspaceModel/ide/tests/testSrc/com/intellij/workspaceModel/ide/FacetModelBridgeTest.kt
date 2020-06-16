// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.mock.MockFacet
import com.intellij.facet.mock.MockFacetType
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.ModifiableFacetModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class FacetModelBridgeTest {
  @Rule
  @JvmField
  var application = ApplicationRule()

  @Rule
  @JvmField
  var temporaryDirectoryRule = TemporaryDirectory()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var project: Project
  private lateinit var module: Module

  @Before
  fun prepareProject() {
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)
    module = createModule()
    runWriteActionAndWait { FacetType.EP_NAME.point.registerExtension(MockFacetType(), module) }
  }

  @Test
  fun `test changed facet config saved correctly`() = WriteCommandAction.runWriteCommandAction(project) {
    val facetData = "mock"
    val facet = MockFacet(module, facetData)
    getFacetManager().createModifiableModel().let { modifiableModel ->
      modifiableModel.addFacet(facet)
      Assert.assertTrue(facet.configuration.data.isEmpty())
      facet.configuration.data = facetData
      modifiableModel.commit()
    }
    val facetConfigXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }

    val facetByType = getFacetManager().getFacetByType(MockFacetType.ID)
    assertNotNull(facetByType)
    assertEquals(facetData, facetByType!!.configuration.data)

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage
    val facetEntity = entityStorage.current.entities(FacetEntity::class.java).first()
    assertEquals(facetConfigXml, facetEntity.configurationXmlTag)

    getFacetManager().createModifiableModel().let { modifiableModel ->
      modifiableModel as ModifiableFacetModelBridge
      assertEquals(facetConfigXml, modifiableModel.getEntity(facet)!!.configurationXmlTag)
    }
  }

  private fun createModule(): Module = runWriteActionAndWait {
    ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(File(project.basePath, "test.iml").path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }
  }

  private fun getFacetManager(): FacetManager {
    return FacetManager.getInstance(module)
  }
}