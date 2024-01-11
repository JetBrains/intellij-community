// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.facet.FacetManager
import com.intellij.facet.impl.ui.FacetEditorImpl
import com.intellij.facet.mock.MockFacetEditorContext
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ui.configuration.projectRoot.FacetConfigurable
import com.intellij.platform.backend.workspace.WorkspaceModel
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

class KotlinFacetBridgeTest : KotlinFacetTestCase() {
    fun testSimpleKotlinFacetCreate() {
        getKotlinFacet()
        checkStorageForEntityAndFacet()
    }

    fun testEditorTabConfigurationOnFacetCreation() {
        val facet = getKotlinFacet()
        val editorContext: FacetEditorContext = MockFacetEditorContext(getKotlinFacet())
        FacetEditorImpl(editorContext, facet.configuration).let {
            it.getComponent()
            it.reset()
            assertNotNull(it.component)
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

        fireFacetChangedAndValidateKotlinFacet(mainFacet)
    }

    fun testCreateFacetEnableHmpp() {
        val mainFacet = getKotlinFacet()
        checkStorageForEntityAndFacet()

        mainFacet.configuration.settings.isHmppEnabled = true

        fireFacetChangedAndValidateKotlinFacet(mainFacet)
    }

    private fun fireFacetChangedAndValidateKotlinFacet(mainFacet: KotlinFacet) {
        val allFacets = FacetManager.getInstance(myModule).allFacets
        assertSize(1, allFacets)
        assertSame(mainFacet, allFacets[0])

        allFacets.forEach { facet -> FacetManager.getInstance(myModule).facetConfigurationChanged(facet) }
        checkStorageForEntityAndFacet()
    }

    private fun getKotlinFacet(): KotlinFacet {
        val facetManager = FacetManager.getInstance(myModule)
        assertSize(1, facetManager.allFacets)
        return facetManager.allFacets[0] as KotlinFacet
    }

    private fun checkStorageForEntityAndFacet(facetName: String = "Kotlin") {
        val entityStorage = WorkspaceModel.getInstance(myProject).currentSnapshot
        val entities: List<KotlinSettingsEntity> = entityStorage.entities(KotlinSettingsEntity::class.java).toList()
        assertSize(1, entities)
        val entity = entities[0]
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
        ) //// source roots
        //val facetSourceRoots = facet.externalSource.also { println(it) }
        //val entitySourceRoots = entity.sourceRoots.also { println(it) }
        //val allModuleSourceRoots = entity.module.sourceRoots.map { it.url.url }.also { println(it) }
        //val entitySourceRootsValid = facetSourceRoots == entitySourceRoots
        //val allModuleSourceRootsValid = facetSourceRoots == allModuleSourceRoots && entitySourceRoots.isEmpty()
        //val sourceRootsValid = entitySourceRootsValid || allModuleSourceRootsValid
        //assertTrue("Invalid source roots", sourceRootsValid)

        // web roots
        //val facetWebRoots = facet.webRoots.map { WebRootData(it.directoryUrl, it.relativePath) }
        //val entityWebRoots = entity.webRoots
        //assertEquals(facetWebRoots, entityWebRoots)

        // //config files
        //val facetConfigFiles = facet.descriptorsContainer.configFiles.map { ConfigFileItem(it.info.metaData.id, it.url) }
        //val entityConfigFiles = entity.configFileItems
        //assertEquals(facetConfigFiles.size, entityConfigFiles.size)
    }
}