// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.facet.FacetManager
import com.intellij.facet.impl.ui.FacetEditorImpl
import com.intellij.facet.mock.MockFacetEditorContext
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.platform.backend.workspace.WorkspaceModel
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.idea.workspaceModel.deserializeExternalSystemTestRunTask

class KotlinFacetBridgeTest : KotlinFacetTestCase() {
    fun testSimpleKotlinFacetCreate() {
        getKotlinFacet()
        checkStorageForEntityAndFacet()
    }

    fun testEditorTabConfigurationOnFacetCreation() {
        val facet = getKotlinFacet()
        val editorContext: FacetEditorContext = MockFacetEditorContext(getKotlinFacet())
        var facetEditorTab: FacetEditorImpl? = null
        try {
            facetEditorTab = FacetEditorImpl(editorContext, facet.configuration)
            facetEditorTab.let {
                it.getComponent()
                it.reset()
                assertNotNull(it.component)
            }
        } finally {
            facetEditorTab?.disposeUIResources()
        }
    }

    fun testFacetRenameAndRemove() {
        val oldFacetName = "Kotlin"
        val newFacetName = "NewFacetName"
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        val facetManager = FacetManager.getInstance(myModule)
        var modifiableModel = facetManager.createModifiableModel()
        assertEquals(oldFacetName, modifiableModel.getFacetName(mainFacet))
        modifiableModel.rename(mainFacet, newFacetName)
        checkStorageForEntityAndFacet()
        assertEquals(oldFacetName, modifiableModel.getFacetName(mainFacet))
        assertEquals(newFacetName, modifiableModel.getNewName(mainFacet))

        runWriteActionAndWait {
            modifiableModel.commit()
        }

        assertEquals(newFacetName, mainFacet.name)
        checkStorageForEntityAndFacet(facetName = newFacetName)

        modifiableModel = facetManager.createModifiableModel()
        modifiableModel.removeFacet(mainFacet)
        runWriteActionAndWait {
            modifiableModel.commit()
        }

        val entityStorage = WorkspaceModel.getInstance(myProject).currentSnapshot
        assertEmpty(entityStorage.entities(KotlinSettingsEntity::class.java).toList())
        assertEmpty(facetManager.allFacets)
    }

    fun testCreateFacetWithDisabledUseProjectSettingsFlag() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        mainFacet.configuration.settings.useProjectSettings = false

        fireFacetChangedEvent(mainFacet)
        checkStorageForEntityAndFacet()
    }

    fun testCreateFacetEnableHmpp() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        mainFacet.configuration.settings.isHmppEnabled = true

        fireFacetChangedEvent(mainFacet)
        checkStorageForEntityAndFacet()
    }

    fun testCreateFacetExternalSystemProjectId() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        mainFacet.configuration.settings.externalSystemRunTasks =
            listOf(ExternalSystemTestRunTask("taskName", "externalSystemProjectId", "targetName", "kotlinPlatformId"))

        fireFacetChangedEvent(mainFacet)
        checkStorageForEntityAndFacet()
    }

    fun testCreateFacetAndCheckNullableTypes() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        mainFacet.configuration.settings.compilerArguments = null
        mainFacet.configuration.settings.compilerSettings = null
        mainFacet.configuration.settings.targetPlatform = null
        mainFacet.configuration.settings.productionOutputPath = null
        mainFacet.configuration.settings.testOutputPath = null

        fireFacetChangedEvent(mainFacet)
        checkStorageForEntityAndFacet()
    }

    fun testChangeCompilerSettingsAfterCreation() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        val additionalArgs = "foo"
        val scriptTemplates = "bar"
        // setup initial compiler settings
        mainFacet.configuration.settings.compilerSettings = CompilerSettings().apply {
            additionalArguments = additionalArgs
        }
        fireFacetChangedEvent(mainFacet)

        // change compiler settings after setup
        mainFacet.configuration.settings.compilerSettings?.scriptTemplates = scriptTemplates
        fireFacetChangedEvent(mainFacet)

        // check that both changes were applied
        assertTrue(getEntity().compilerSettings!!.additionalArguments == additionalArgs)
        assertTrue(getEntity().compilerSettings!!.scriptTemplates == scriptTemplates)
    }

    private fun getEntity(): KotlinSettingsEntity {
        val entityStorage = WorkspaceModel.getInstance(myProject).currentSnapshot
        val entities: List<KotlinSettingsEntity> = entityStorage.entities(KotlinSettingsEntity::class.java).toList()
        assertSize(1, entities)
        return entities[0]
    }

    private fun checkStorageForEntityAndFacet(facetName: String = "Kotlin") {
        val entity = getEntity()
        assertEquals(facetName, entity.name)
        assertEquals(myModule.name, entity.module.name)

        val facetManager = FacetManager.getInstance(myModule)
        assertSize(1, facetManager.allFacets)
        val facet = facetManager.allFacets[0] as KotlinFacet
        assertEquals(facetName, facet.name)

        assertTrue(
            "Use project settings differs. Entity: ${entity.useProjectSettings}, facet: ${facet.configuration.settings.useProjectSettings}",
            entity.useProjectSettings == facet.configuration.settings.useProjectSettings
        )
        assertTrue(
            "isHmppEnabled differs. Entity: ${entity.isHmppEnabled}, facet: ${facet.configuration.settings.isHmppEnabled}",
            entity.isHmppEnabled == facet.configuration.settings.isHmppEnabled
        )
        assertTrue(
            "externalSystemRunTasks differs. Entity: ${entity.externalSystemRunTasks}, facet: ${facet.configuration.settings.externalSystemRunTasks}",
            entity.externalSystemRunTasks.map { deserializeExternalSystemTestRunTask(it) } == facet.configuration.settings.externalSystemRunTasks
        )
        assertTrue(
            "compilerArguments is not null",
            entity.compilerArguments == null
        )
        assertTrue(
            "compilerSettings is not null",
            entity.compilerSettings == null
        )
        assertTrue(
            "targetPlatform differs. Entity: ${entity.targetPlatform}, facet: ${facet.configuration.settings.targetPlatform}",
            entity.targetPlatform == null
        )
        assertTrue(
            "productionOutputPath differs. Entity: ${entity.productionOutputPath}, facet: ${facet.configuration.settings.productionOutputPath}",
            entity.productionOutputPath == facet.configuration.settings.productionOutputPath
        )
        assertTrue(
            "testOutputPath differs. Entity: ${entity.testOutputPath}, facet: ${facet.configuration.settings.testOutputPath}",
            entity.testOutputPath == facet.configuration.settings.testOutputPath
        )
    }
}