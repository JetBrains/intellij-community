// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

private const val IN_JAR: String = "testData/script/templatesFromDependencies/inJar/"
private const val IN_PRODUCTION_RESOURCES: String = "testData/script/templatesFromDependencies/inProductionResources/"

@TestRoot("base/scripting/scripting")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("testData/script/templatesFromDependencies")
class DefinitionFromDependenciesProviderTest : AbstractDefinitionFromDependenciesProviderTest() {

    private fun runTest(path: String) {
        KotlinTestUtils.runTest({ doTest(it) }, this, path)
    }

    @TestMetadata("inJar")
    fun testInJar() {
        runTest("testData/script/templatesFromDependencies/inJar/")
    }

    @TestMetadata("inProductionResources")
    fun testInProductionResources() {
        runTest(IN_PRODUCTION_RESOURCES)
    }

    @TestMetadata("inTests")
    fun testInTests() {
        runTest("testData/script/templatesFromDependencies/inTests/")
    }

    @TestMetadata("outsideRoots")
    fun testOutsideRoots() {
        runTest("testData/script/templatesFromDependencies/outsideRoots/")
    }

    fun testReRunReturnsConsistentTemplates() {
        runTest("testData/script/templatesFromDependencies/inJar/")

        val provider = DefinitionFromDependenciesProvider(project)
        val firstFqns = provider.definitionClasses()
        val firstClasspath = provider.getTemplateClasspath()
        val secondFqns = provider.definitionClasses()
        val secondClasspath = provider.getTemplateClasspath()

        assertEquals("Repeated discovery on unchanged project state must return equal FQNs", firstFqns, secondFqns)
        assertEquals("Repeated discovery on unchanged project state must return equal classpath", firstClasspath, secondClasspath)
        assertFalse("Discovery should have found at least one template", firstFqns.isEmpty())
    }

    fun testMarkerOriginIsRecordedForEachDiscoveredFqn() {
        runTest(IN_JAR)

        val provider = DefinitionFromDependenciesProvider(project)

        assertEquals("A marker-only FQN must be discovered", listOf("MyTemplate1"), provider.definitionClasses())
        assertEquals("A marker origin must name the root that holds it", "templates.jar", provider.markerOriginFor("MyTemplate1")?.rootName)
    }

    fun testExplicitFqnWithoutMarkerReportsNoMarkerFile() {
        runTest(IN_JAR)
        KotlinScriptingSettings.getInstance(project).update { it.copy(explicitTemplateClassNames = "com.example.Explicit") }

        val provider = DefinitionFromDependenciesProvider(project)

        assertTrue("An explicit FQN must be included", provider.definitionClasses().contains("com.example.Explicit"))
        assertNull("An explicit FQN with no marker must report no marker file", provider.markerOriginFor("com.example.Explicit"))
    }

    fun testFqnPresentInBothSourcesAppearsOnceAndKeepsItsMarker() {
        runTest(IN_JAR)
        KotlinScriptingSettings.getInstance(project).update { it.copy(explicitTemplateClassNames = "MyTemplate1") }

        val provider = DefinitionFromDependenciesProvider(project)

        assertEquals("An FQN in both sources must appear once", listOf("MyTemplate1"), provider.definitionClasses())
        assertNotNull(
            "A marker is the more specific origin, so it must win over the explicit entry",
            provider.markerOriginFor("MyTemplate1"),
        )
    }

    fun testExplicitAndDiscoveredClasspathsAreDeduplicated() {
        runTest(IN_JAR)

        val discovered = DefinitionFromDependenciesProvider(project).getTemplateClasspath().map { it.pathString }
        assertFalse("Discovery must find at least one classpath entry", discovered.isEmpty())

        KotlinScriptingSettings.getInstance(project).update { it.copy(explicitTemplateClasspath = discovered.joinToString("\n")) }
        val merged = DefinitionFromDependenciesProvider(project).getTemplateClasspath().map { it.pathString }

        assertEquals("An entry present in both sources must appear once", merged.distinct(), merged)
        assertEquals(discovered.toSet(), merged.toSet())
    }

    fun testSettingsUpdateBumpsModificationTracker() {
        val tracker = ScriptDefinitionsModificationTracker.getInstance(project)
        val settings = KotlinScriptingSettings.getInstance(project)

        val before = tracker.modificationCount
        settings.update { it.copy(explicitTemplateClassNames = "com.example.Foo") }
        val afterChange = tracker.modificationCount
        assertTrue(
            "ScriptDefinitionsModificationTracker must advance when settings state changes (was $before, now $afterChange)",
            afterChange > before
        )

        settings.update { it }
        val afterNoOp = tracker.modificationCount
        assertEquals(
            "Tracker must not advance when settings state is unchanged",
            afterChange,
            afterNoOp
        )
    }

    fun testBuildInvalidatesDiscovery() {
        runTest(IN_PRODUCTION_RESOURCES)

        val provider = DefinitionFromDependenciesProvider(project)
        assertEmpty(provider.definitionClasses())
        val definitionsTracker = ScriptDefinitionsModificationTracker.getInstance(project)
        val definitionsCount = definitionsTracker.modificationCount
        val templatesDirectory = getOrCreateProjectBaseDir().findFileByRelativePath("module/classes/$SCRIPT_TEMPLATE_MARKER_DIR")!!

        runWriteAction {
            templatesDirectory.createChildData(this, "MyTemplate.classname")
        }
        refreshDefinitionsAfterBuild()

        assertTrue("A build that adds a marker file must reload script definitions", definitionsTracker.modificationCount > definitionsCount)
        assertEquals(listOf("MyTemplate"), provider.definitionClasses())
    }

    fun testBuildWithoutMarkerChangeKeepsDefinitions() {
        runTest(IN_JAR)

        val definitionsTracker = ScriptDefinitionsModificationTracker.getInstance(project)
        val definitionsCount = definitionsTracker.modificationCount

        refreshDefinitionsAfterBuild()

        assertEquals("A build that changes no marker file must not reload script definitions", definitionsCount, definitionsTracker.modificationCount)
    }

    fun testMarkerInTestScopeLibraryIsDiscovered() {
        runTest(IN_JAR)

        val module = ModuleManager.getInstance(project).modules.single { it.name.endsWith("_module") }
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.orderEntries.filterIsInstance<LibraryOrderEntry>().forEach { it.scope = DependencyScope.TEST }
        }

        val provider = DefinitionFromDependenciesProvider(project)
        assertEquals(listOf("MyTemplate1"), provider.definitionClasses())
        assertTrue("The marker root must stay on the classpath", provider.getTemplateClasspath().any { it.pathString.endsWith("templates.jar") })
    }

    fun testTestOutputIsNotOnDefinitionClasspath() {
        runTest(IN_PRODUCTION_RESOURCES)

        val module = ModuleManager.getInstance(project).modules.single { it.name.endsWith("_module") }
        val moduleDirectory = getOrCreateProjectBaseDir().findChild("module")!!
        val (testSources, testOutput) = runWriteAction {
            moduleDirectory.createChildDirectory(this, "test") to moduleDirectory.createChildDirectory(this, "test-classes")
        }
        PsiTestUtil.addSourceRoot(module, testSources, true)
        PsiTestUtil.setCompilerOutputPath(module, testOutput.url, true)
        runWriteAction {
            moduleDirectory.findFileByRelativePath("classes/$SCRIPT_TEMPLATE_MARKER_DIR")!!.createChildData(this, "MyTemplate.classname")
        }

        val classpath = DefinitionFromDependenciesProvider(project).getTemplateClasspath().map { it.pathString }
        assertTrue("The production output must be on the classpath", classpath.any { it.endsWith("module/classes") })
        assertFalse("The test output must not be on the classpath", classpath.any { it.endsWith("test-classes") })
    }

    fun testClasspathGuessInDumbModeReloadsDefinitionsInSmartMode() {
        runTest(IN_JAR)
        KotlinScriptingSettings.getInstance(project).update { it.copy(explicitTemplateClassNames = "com.example.Explicit") }

        val definitionsTracker = ScriptDefinitionsModificationTracker.getInstance(project)
        val definitionsCount = definitionsTracker.modificationCount
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            DefinitionFromDependenciesProvider(project).getTemplateClasspath()
        }
        runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

        assertTrue("The end of the dumb mode must reload script definitions", definitionsTracker.modificationCount > definitionsCount)
    }

    fun testDefaultProjectDoesNotQueryFileIndex() {
        val defaultProject = ProjectManager.getInstance().defaultProject
        val settings = KotlinScriptingSettings.getInstance(defaultProject)
        val initialState = settings.state
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        settings.update { it.copy(explicitTemplateClassNames = "com.example.Explicit") }
        try {
            LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
                override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
                    warnings += message
                    return true
                }

                override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
                    errors += message
                    return Action.NONE
                }
            }) {
                val provider = DefinitionFromDependenciesProvider(defaultProject)
                assertEquals(listOf("com.example.Explicit"), provider.definitionClasses())
                assertEmpty(provider.getTemplateClasspath())
            }
        }
        finally {
            settings.update { initialState }
        }

        assertEmpty(warnings.filter { "default project" in it })
        assertEmpty(errors)
    }

    fun testInvalidExplicitClasspathEntryIsSkipped() {
        runTest(IN_JAR)
        val discovered = DefinitionFromDependenciesProvider(project).getTemplateClasspath()
        KotlinScriptingSettings.getInstance(project).update { it.copy(explicitTemplateClasspath = "bad\u0000path") }

        assertEquals(discovered, DefinitionFromDependenciesProvider(project).getTemplateClasspath())
    }

    fun testOutputDirectoryCreatedAfterDiscoveryIsScanned() {
        runTest(IN_PRODUCTION_RESOURCES)

        val module = ModuleManager.getInstance(project).modules.single { it.name.endsWith("_module") }
        val projectDirectory = getOrCreateProjectBaseDir()
        PsiTestUtil.setCompilerOutputPath(module, "${projectDirectory.url}/module/out", false)
        val provider = DefinitionFromDependenciesProvider(project)
        assertEmpty(provider.definitionClasses())

        runWriteAction {
            VfsUtil.createDirectoryIfMissing(projectDirectory, "module/out/$SCRIPT_TEMPLATE_MARKER_DIR")!!.createChildData(this, "MyTemplate.classname")
        }
        refreshDefinitionsAfterBuild()

        assertEquals(listOf("MyTemplate"), provider.definitionClasses())
    }

    fun testDiscoveryRunsInsideReadActionAndOnEdt() {
        runTest(IN_JAR)

        val definitionsTracker = ScriptDefinitionsModificationTracker.getInstance(project)
        definitionsTracker.incModificationCount()
        assertEquals(listOf("MyTemplate1"), runReadActionBlocking { DefinitionFromDependenciesProvider(project).definitionClasses() })
        definitionsTracker.incModificationCount()
        assertEquals(listOf("MyTemplate1"), runInEdtAndGet { DefinitionFromDependenciesProvider(project).definitionClasses() })
    }

    private fun refreshDefinitionsAfterBuild() {
        ScriptDefinitionsBuildListener(project).refreshDefinitions().blockingGet(1, TimeUnit.MINUTES)
    }
}
