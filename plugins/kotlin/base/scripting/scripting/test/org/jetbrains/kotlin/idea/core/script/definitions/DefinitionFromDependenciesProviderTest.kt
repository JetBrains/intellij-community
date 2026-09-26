// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import kotlin.io.path.pathString

private const val IN_JAR: String = "testData/script/templatesFromDependencies/inJar/"

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

}
