// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionSettingsStateComponent
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("base/scripting/scripting.k2")
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
        val firstFqns = provider.getDefinitionClasses().toList()
        val firstClasspath = provider.getDefinitionsClassPath().toList()
        val secondFqns = provider.getDefinitionClasses().toList()
        val secondClasspath = provider.getDefinitionsClassPath().toList()

        assertEquals("Repeated discovery on unchanged project state must return equal FQNs", firstFqns, secondFqns)
        assertEquals("Repeated discovery on unchanged project state must return equal classpath", firstClasspath, secondClasspath)
        assertFalse("Discovery should have found at least one template", firstFqns.isEmpty())
    }

    fun testSettingsUpdateBumpsModificationTracker() {
        val tracker = ScriptDefinitionsModificationTracker.getInstance(project)
        val settings = ScriptDefinitionSettingsStateComponent.getInstance(project)

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
