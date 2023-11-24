// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerListener
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.BaseIdeSerializationContext
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.workspaceModel.*
import org.junit.*

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class KotlinFacetEventListenerTest {
    companion object {
        @JvmField
        @ClassRule
        val appRule = ApplicationRule()
    }

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val tempDirManager = TemporaryDirectory()

    @Before
    fun setUp() {
        //TODO: remove after enabling by default
        Registry.get("workspace.model.kotlin.facet.bridge").setValue(true)
        Assume.assumeTrue("Execute only if kotlin facet bridge enabled", KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled)
        TestCase.assertTrue("KotlinFacetContributor is not registered",
                            !WorkspaceFacetContributor.EP_NAME.extensions.none { (it as Any) is KotlinFacetContributor })
        TestCase.assertTrue("KotlinModuleSettingsSerializer is not registered",
                            !BaseIdeSerializationContext.CUSTOM_FACET_RELATED_ENTITY_SERIALIZER_EP.extensions.none { (it as Any) is KotlinModuleSettingsSerializer })
    }

    @Test
    fun compareFacetEventsWithNewImplementation() {
        val eventFromOldImplementation = executeWithKotlinFacetBridge(false) {
            return@executeWithKotlinFacetBridge basicScenarioWithFacetEvents("legacy")
        }
        val eventFromNewImplementation = executeWithKotlinFacetBridge(true) {
            return@executeWithKotlinFacetBridge basicScenarioWithFacetEvents("workspaceModel")
        }
        TestCase.assertEquals(eventFromOldImplementation, eventFromNewImplementation)
    }

    @Test
    fun checkFacetUpdateUseProjectSettingsViaWorkspaceModelModification() {
        val eventFromNewImplementation = executeWithKotlinFacetBridge(true) {
            return@executeWithKotlinFacetBridge facetUpdateUseProjectSettingsViaEntities()
        }
        TestCase.assertEquals(
            """
        before added[Kotlin]
        added[Kotlin]
        changed[Kotlin]
        before removed[Kotlin]
        removed[Kotlin]
        
      """.trimIndent(), eventFromNewImplementation
        )
    }

    @Test
    fun checkFacetRenameViaWorkspaceModelModification() {
        val eventFromNewImplementation = executeWithKotlinFacetBridge(true) {
            return@executeWithKotlinFacetBridge facetRenameViaEntities()
        }
        TestCase.assertEquals(
            """
        before added[Kotlin]
        added[Kotlin]
        before renamed[Kotlin]
        renamed[New Name]
        before removed[New Name]
        removed[New Name]
        
      """.trimIndent(), eventFromNewImplementation
        )
    }

    private fun facetRenameViaEntities(): String = runBlocking {
        val listener = MyFacetManagerListener()
        createOrLoadProject(tempDirManager, useDefaultProjectSettings = false) { project ->
            runInEdtAndWait {
                runWriteActionAndWait {
                    project.messageBus.connect().subscribe(FacetManager.FACETS_TOPIC, listener)
                    val moduleManager = ModuleManager.getInstance(project)

                    val modifiableModel = moduleManager.getModifiableModel()
                    modifiableModel.newModule("independent/independent.iml", "myModuleType")
                    modifiableModel.commit()

                    val workspaceModel = WorkspaceModel.getInstance(project)
                    val moduleEntity = workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).first()
                    workspaceModel.updateProjectModel("add kotlin setting entity") {
                        it.addEntity(
                            KotlinSettingsEntity(KotlinFacetType.INSTANCE.presentableName,
                                                 ModuleId(""),
                                                 emptyList(),
                                                 emptyList(),
                                                 true,
                                                 emptyList(),
                                                 emptyList(),
                                                 emptySet(),
                                                 "",
                                                 "",
                                                 emptyList(),
                                                 false,
                                                 "",
                                                 false,
                                                 emptyList(),
                                                 KotlinModuleKind.DEFAULT,
                                                 "",
                                                 CompilerSettingsData("", "", "", true, "lib", false),
                                                 "",
                                                 object : EntitySource {}) {
                                module = moduleEntity
                            }
                        )
                    }

                    val module = moduleManager.modules[0]
                    val facetManager = FacetManager.getInstance(module)
                    UsefulTestCase.assertSize(1, facetManager.allFacets)
                    val kotlinFacet = facetManager.allFacets[0] as KotlinFacet
                    TestCase.assertEquals("Kotlin", kotlinFacet.name)

                    workspaceModel.updateProjectModel("rename") {
                        val kotlinSettingsEntity = it.entities(KotlinSettingsEntity::class.java).first()
                        it.modifyEntity(kotlinSettingsEntity) {
                            name = "New Name"
                        }
                    }
                    UsefulTestCase.assertSize(1, facetManager.allFacets)
                    val sameKotlinFacet = facetManager.allFacets[0] as KotlinFacet
                    TestCase.assertSame(kotlinFacet, sameKotlinFacet)
                    TestCase.assertEquals("New Name", sameKotlinFacet.name)

                    workspaceModel.updateProjectModel("remove") {
                        val kotlinSettingsEntity = it.entities(KotlinSettingsEntity::class.java).first()
                        it.removeEntity(kotlinSettingsEntity)
                    }
                    UsefulTestCase.assertSize(0, facetManager.allFacets)
                    TestCase.assertTrue(kotlinFacet.isDisposed)
                    TestCase.assertTrue(sameKotlinFacet.isDisposed)
                }
            }
        }
        return@runBlocking listener.events
    }

    private fun facetUpdateUseProjectSettingsViaEntities(): String = runBlocking {
        val listener = MyFacetManagerListener()
        createOrLoadProject(tempDirManager, useDefaultProjectSettings = false) { project ->
            runInEdtAndWait {
                runWriteActionAndWait {
                    project.messageBus.connect().subscribe(FacetManager.FACETS_TOPIC, listener)
                    val moduleManager = ModuleManager.getInstance(project)

                    val modifiableModel = moduleManager.getModifiableModel()
                    modifiableModel.newModule("independent/independent.iml", "myModuleType")
                    modifiableModel.commit()

                    val workspaceModel = WorkspaceModel.getInstance(project)
                    val moduleEntity = workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).first()
                    workspaceModel.updateProjectModel("add Kotlin setting entity") {
                        it.addEntity(
                            KotlinSettingsEntity(KotlinFacetType.INSTANCE.presentableName,
                                                 ModuleId(""),
                                                 emptyList(),
                                                 emptyList(),
                                                 true,
                                                 emptyList(),
                                                 emptyList(),
                                                 emptySet(),
                                                 "",
                                                 "",
                                                 emptyList(),
                                                 false,
                                                 "",
                                                 false,
                                                 emptyList(),
                                                 KotlinModuleKind.DEFAULT,
                                                 "",
                                                 CompilerSettingsData("", "", "", true, "lib", false),
                                                 "",
                                                 object : EntitySource {}) {
                                module = moduleEntity
                            }
                        )
                    }

                    val module = moduleManager.modules[0]
                    val facetManager = FacetManager.getInstance(module)
                    UsefulTestCase.assertSize(1, facetManager.allFacets)
                    val kotlinFacet = facetManager.allFacets[0] as KotlinFacet
                    TestCase.assertEquals("Kotlin", kotlinFacet.name)

                    workspaceModel.updateProjectModel("Don't use project settings") {
                        val kotlinSettingsEntity = it.entities(KotlinSettingsEntity::class.java).first()
                        it.modifyEntity(kotlinSettingsEntity) {
                            useProjectSettings = false
                        }
                    }
                    UsefulTestCase.assertSize(1, facetManager.allFacets)
                    var sameKotlinFacet = facetManager.allFacets[0] as KotlinFacet
                    TestCase.assertSame(kotlinFacet, sameKotlinFacet)

                    UsefulTestCase.assertSize(1, facetManager.allFacets)
                    sameKotlinFacet = facetManager.allFacets[0] as KotlinFacet
                    TestCase.assertSame(kotlinFacet, sameKotlinFacet)

                    workspaceModel.updateProjectModel("remove") {
                        val kotlinSettingsEntity = it.entities(KotlinSettingsEntity::class.java).first()
                        it.removeEntity(kotlinSettingsEntity)
                    }
                    UsefulTestCase.assertSize(0, facetManager.allFacets)
                    TestCase.assertTrue(kotlinFacet.isDisposed)
                    TestCase.assertTrue(sameKotlinFacet.isDisposed)
                }
            }
        }
        return@runBlocking listener.events
    }

    private fun executeWithKotlinFacetBridge(facetBridgeEnabled: Boolean, action: () -> Any): Any {
        val registryValue = Registry.get("workspace.model.kotlin.facet.bridge")
        val initValue = registryValue.asBoolean()
        try {
            registryValue.setValue(facetBridgeEnabled)
            return action()
        } finally {
            registryValue.setValue(initValue)
        }
    }

    private fun basicScenarioWithFacetEvents(implementation: String): String = runBlocking {
        val listener = MyFacetManagerListener()
        createOrLoadProject(tempDirManager, useDefaultProjectSettings = false) { project ->
            runInEdtAndWait {
                runWriteActionAndWait {
                    project.messageBus.connect().subscribe(FacetManager.FACETS_TOPIC, listener)
                    val moduleManager = ModuleManager.getInstance(project)

                    val modifiableModuleModel = moduleManager.getModifiableModel()
                    modifiableModuleModel.newModule(implementation + "independent/independent.iml", "myModuleType")
                    modifiableModuleModel.commit()
                    FacetUtil.addFacet(moduleManager.modules[0], KotlinFacetType.INSTANCE)

                    val module = moduleManager.modules[0]
                    val facetManager = FacetManager.getInstance(module)
                    val facets = facetManager.allFacets
                    TestCase.assertTrue("facets must not be empty", facets.isNotEmpty())
                    val kotlinFacet = facets[0] as KotlinFacet

                    var modifiableModel = facetManager.createModifiableModel()
                    modifiableModel.rename(kotlinFacet, "New_Name")
                    modifiableModel.commit()

                    modifiableModel = facetManager.createModifiableModel()
                    modifiableModel.removeFacet(kotlinFacet)
                    modifiableModel.commit()
                }
            }
        }
        return@runBlocking listener.events
    }

    private class MyFacetManagerListener : FacetManagerListener {
        private val myEvents = StringBuilder()
        override fun beforeFacetAdded(facet: Facet<*>) {
            myEvents.append("before added[").append(facet.name).append("]\n")
        }

        override fun beforeFacetRemoved(facet: Facet<*>) {
            myEvents.append("before removed[").append(facet.name).append("]\n")
        }

        override fun beforeFacetRenamed(facet: Facet<*>) {
            myEvents.append("before renamed[").append(facet.name).append("]\n")
        }

        override fun facetRenamed(facet: Facet<*>, oldName: String) {
            myEvents.append("renamed[").append(facet.name).append("]\n")
        }

        override fun facetConfigurationChanged(facet: Facet<*>) {
            myEvents.append("changed[").append(facet.name).append("]\n")
        }

        override fun facetAdded(facet: Facet<*>) {
            myEvents.append("added[").append(facet.name).append("]\n")
        }

        override fun facetRemoved(facet: Facet<*>) {
            myEvents.append("removed[").append(facet.name).append("]\n")
        }

        val events: String
            get() {
                val eventsAsString = myEvents.toString()
                myEvents.setLength(0)
                return eventsAsString
            }
    }
}