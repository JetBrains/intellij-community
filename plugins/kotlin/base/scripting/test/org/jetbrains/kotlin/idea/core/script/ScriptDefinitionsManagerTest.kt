// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JavaIoSerializableObjectMustHaveReadResolve")

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.command.impl.DummyProject
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptingHostConfiguration

class ScriptDefinitionsManagerTest {

    private val project = DummyProject.getInstance()
    private val manager = ScriptDefinitionsManagerUnderTest(project)

    private val sourceA = TestSource()
    private val sourceB = TestSource()

    private val definitionA = TestDefinition("A")
    private val definitionB = TestDefinition("B")
    private val definitionC = TestDefinition("C")

    private val script = object : SourceCode {
        override val locationId: String = "script.kts"
        override val name: String = ""
        override val text: String = "println()"
    }


    @Test
    fun `Loading works after unsuccessful attempt`() {
        assertTrue(manager.reloadDefinitionsBy(TestSource()).isEmpty())

        assertEquals(
            listOf(definitionA, definitionB),
            manager.reloadDefinitionsBy(TestSource().returning(definitionA, definitionB))
        )
    }

    @Test
    fun `Search for definition triggers sources not yet contributed to cache`() {
        val definitionToLoad = TestDefinition("B") { true }

        manager.definitionSources = listOf(
            sourceA.returning(TestDefinition("A") { false }),
            sourceB.returning(definitionToLoad)
        )

        manager.reloadDefinitionsBy(sourceA) // DefinitionA is supposed to be cached, DefinitionB not
        assertEquals(definitionToLoad, manager.findDefinition(script))
    }

    @Test
    fun `Reloading keeps working after all sources contributed nothing`() {
        manager.definitionSources = listOf(sourceA)
        assertNull(manager.findDefinition(script))

        val definitionToLoad = TestDefinition("A") { true }
        manager.definitionSources = listOf(sourceA.returning(definitionToLoad)) // the same sources, but now A can load a definition
        assertEquals(definitionToLoad, manager.findDefinition(script))
    }

    @Test
    fun `Only requested source loads its definitions`() {
        assertEquals(
            listOf(definitionA, definitionB),
            manager.reloadDefinitionsBy(sourceA.returning(definitionA, definitionB))
        )

        assertEquals(
            listOf(definitionA, definitionB, definitionC),
            manager.reloadDefinitionsBy(sourceB.returning(definitionC))
        )

        assertEquals(
            listOf(definitionB, definitionC),
            manager.reloadDefinitionsBy(sourceA.returning(definitionB))
        )

        assertEquals(
            listOf(definitionB),
            manager.reloadDefinitionsBy(sourceB.returning())
        )
    }

    @Test
    fun `Source order is respected`() {
        val managerWithABOrder = ScriptDefinitionsManagerUnderTest(project).apply {
            definitionSources = listOf(
                sourceA.returning(definitionA),
                sourceB.returning(definitionB, definitionC),
            )
        }

        assertEquals(
            listOf(definitionA, definitionB, definitionC),
            managerWithABOrder.allDefinitions
        )

        val managerWithBAOrder = ScriptDefinitionsManagerUnderTest(project).apply {
            definitionSources = listOf(
                sourceB.returning(definitionB, definitionC),
                sourceA.returning(definitionA),
            )
        }

        assertEquals(
            listOf(definitionB, definitionC, definitionA),
            managerWithBAOrder.reloadDefinitions()
        )
    }

    @Test
    fun `Cached definitions used until reloaded`() {
        manager.definitionSources = listOf(
            sourceA.returning(definitionA),
            sourceB.returning(definitionB),
        )

        assertEquals(
            listOf(definitionA, definitionB),
            manager.allDefinitions
        )

        manager.definitionSources = listOf(
            sourceA.returning(definitionA),
            sourceB.returning(definitionB, definitionC /* appeared */),
        )

        assertEquals(
            listOf(definitionA, definitionB), /* still the same list */
            manager.allDefinitions
        )

        manager.reloadDefinitions()

        assertEquals(
            listOf(definitionA, definitionB, definitionC /* now */),
            manager.allDefinitions
        )
    }

    @Test
    fun `Reordering applied after reload`() {
        manager.definitionSources = listOf(
            sourceA.returning(definitionA),
            sourceB.returning(definitionB, definitionC),
        )

        assertEquals(
            listOf(definitionA, definitionB, definitionC),
            manager.allDefinitions
        )

        manager.settings = KotlinScriptingSettings(project).apply {
            setOrder(definitionC, 0)
            setOrder(definitionA, 1)
            setOrder(definitionB, 2)
        }

        assertEquals(
            listOf(definitionA, definitionB, definitionC), /* no effect */
            manager.allDefinitions
        )

        assertEquals(
            listOf(definitionC, definitionA, definitionB), /* now */
            manager.reloadDefinitions()
        )
    }


    @Test
    fun `Deactivation affects only current definitions`() {
        manager.definitionSources = listOf(
            sourceA.returning(definitionA),
            sourceB.returning(definitionB, definitionC),
        )

        manager.settings = KotlinScriptingSettings(project).apply {
            setEnabled(definitionA, true)
            setEnabled(definitionB, false)
            setEnabled(definitionC, true)
        }

        assertEquals(
            listOf(definitionA, definitionB, definitionC),
            manager.allDefinitions
        )

        assertEquals(
            listOf(definitionA, definitionC),
            manager.currentDefinitions.toList()
        )
    }

    @Test
    fun `First matching definition provided at search`() {
        val definitionA = TestDefinition("A") { false }
        val definitionB = TestDefinition("B") { false }
        val definitionC = TestDefinition("C") { true }
        val definitionD = TestDefinition("D") { true }

        manager.definitionSources = listOf(
            TestSource().returning(definitionA, definitionB, definitionC, definitionD)
        )

        assertTrue(manager.findDefinition(script)?.name == "C")
    }

    @Test
    fun `None of non-matching definitions is provided at search`() {
        val definitionA = TestDefinition("A") { false }
        val definitionB = TestDefinition("B") { false }

        manager.definitionSources = listOf(
            TestSource().returning(definitionA, definitionB)
        )

        assertNull(manager.findDefinition(script))
    }


    @Test
    fun `Definition order plays role at search`() {
        val definitionA = TestDefinition("A") { true }
        val definitionB = TestDefinition("B") { true }

        manager.definitionSources = listOf(
            /* both match */
            sourceA.returning(definitionA),
            sourceB.returning(definitionB),
        )

        assertTrue(manager.findDefinition(script)?.name == "A")

        manager.settings = KotlinScriptingSettings(project).apply {
            setOrder(definitionB, 0)
            setOrder(definitionA, 1)
        }

        manager.reorderDefinitions()

        assertTrue(manager.findDefinition(script)?.name == "B")
    }

    @Test
    fun `Provided file extensions match known definitions`() {
        manager.definitionSources = listOf(
            sourceA.returning(TestDefinition("A", "a.kts"), TestDefinition("AA", "aa.kts")),
            sourceB.returning(TestDefinition("B", "b.kts")),
        )

        val extensions = manager.getKnownFilenameExtensions()
        extensions.toList() // see KT-63462
        assertEquals(listOf("a.kts", "aa.kts", "b.kts"), extensions.toList())
    }

    @Test
    fun `Matching definition defines whether the code is script`() {

        manager.definitionSources = listOf(
            sourceA.returning(TestDefinition("A") { true })
        )

        assertTrue(manager.isScript(script))

        manager.definitionSources = listOf(
            sourceA.returning(TestDefinition("A") { false }),
            sourceB.returning(TestDefinition("B") { false })
        )
        manager.reloadDefinitions()

        assertFalse(manager.isScript(script))
    }

    @Test
    fun `Initial definition sources order is always preserved`() {
        val defA = TestDefinition("A") { true }

        manager.definitionSources = listOf(
            sourceA.returning(defA),
            sourceB.returning(TestDefinition("B") { true })
        ) // ... definitions are not yet loaded

        manager.reloadDefinitionsBy(sourceB) // def "B" becomes available

        // [sourceA, sourceB] order is expected to be preserved (sourceA is triggered at search)
        val foundDefinition = manager.findDefinition(script)
        assertEquals(defA, foundDefinition)
    }
}

private class ScriptDefinitionsManagerUnderTest(val project: Project) : ScriptDefinitionsManager(project) {
    var definitionSources: List<ScriptDefinitionsSource> = emptyList()
    var settings: KotlinScriptingSettings = KotlinScriptingSettings(project)

    override fun getSources(): List<ScriptDefinitionsSource> = definitionSources

    override fun getKotlinScriptingSettings(): KotlinScriptingSettings = settings

    override fun applyDefinitionsUpdate() {}

    override fun tryGetScriptDefinitionFast(locationId: String): ScriptDefinition? = null

    override fun isScratchFile(script: SourceCode): Boolean = false

    override fun executeUnderReadLock(block: () -> Unit) = block()
}

private open class TestSource : ScriptDefinitionsSource {
    private var toReturn: Sequence<ScriptDefinition> = emptySequence()

    fun returning(vararg toReturn: ScriptDefinition): TestSource {
        this.toReturn = toReturn.asSequence()
        return this
    }

    override val definitions: Sequence<ScriptDefinition>
        get() = toReturn
}

private open class TestDefinition(
    final override val name: String,
    override val fileExtension: String = ".kts",
    private val scriptDetector: ((SourceCode) -> Boolean)? = null,
) : ScriptDefinition() {

    override val annotationsForSamWithReceivers: List<String>
        get() = TODO("Not yet implemented")
    override val baseClassType: KotlinType
        get() = TODO("Not yet implemented")
    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = TODO("Not yet implemented")
    override val compilerOptions: Iterable<String>
        get() = TODO("Not yet implemented")
    override val contextClassLoader: ClassLoader?
        get() = TODO("Not yet implemented")
    override val definitionId: String = "$name-id"
    override val evaluationConfiguration: ScriptEvaluationConfiguration?
        get() = TODO("Not yet implemented")
    override val hostConfiguration: ScriptingHostConfiguration
        get() = TODO("Not yet implemented")

    @Deprecated("Use configurations instead")
    override val legacyDefinition: KotlinScriptDefinition
        get() = TODO("Not yet implemented")

    override fun isScript(script: SourceCode): Boolean = scriptDetector?.invoke(script) ?: false
}
