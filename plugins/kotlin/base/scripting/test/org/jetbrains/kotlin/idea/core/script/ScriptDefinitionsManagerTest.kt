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

    private object SourceA : TestSource()
    private object SourceB : TestSource()

    private object DefinitionA : TestDefinition("A")
    private object DefinitionB : TestDefinition("B")
    private object DefinitionC : TestDefinition("C")


    @Test
    fun `Loading works after unsuccessful attempt`() {
        assertTrue(manager.reloadDefinitionsBy(TestSource()).isEmpty())

        assertEquals(
            listOf(DefinitionA, DefinitionB),
            manager.reloadDefinitionsBy(TestSource().returning(DefinitionA, DefinitionB))
        )
    }

    @Test
    fun `Only requested source loads its definitions`() {
        assertEquals(
            listOf(DefinitionA, DefinitionB),
            manager.reloadDefinitionsBy(SourceA.returning(DefinitionA, DefinitionB))
        )

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC),
            manager.reloadDefinitionsBy(SourceB.returning(DefinitionC))
        )

        assertEquals(
            listOf(DefinitionB, DefinitionC),
            manager.reloadDefinitionsBy(SourceA.returning(DefinitionB))
        )

        assertEquals(
            listOf(DefinitionB),
            manager.reloadDefinitionsBy(SourceB.returning())
        )
    }

    @Test
    fun `Source order is respected`() {
        val managerWithABOrder = ScriptDefinitionsManagerUnderTest(project).apply {
            definitionSources = listOf(
                SourceA.returning(DefinitionA),
                SourceB.returning(DefinitionB, DefinitionC),
            )
        }

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC),
            managerWithABOrder.getAllDefinitions()
        )

        val managerWithBAOrder = ScriptDefinitionsManagerUnderTest(project).apply {
            definitionSources = listOf(
                SourceB.returning(DefinitionB, DefinitionC),
                SourceA.returning(DefinitionA),
            )
        }

        assertEquals(
            listOf(DefinitionB, DefinitionC, DefinitionA),
            managerWithBAOrder.reloadScriptDefinitions()
        )
    }

    @Test
    fun `Cached definitions used until reloaded`() {
        manager.definitionSources = listOf(
            SourceA.returning(DefinitionA),
            SourceB.returning(DefinitionB),
        )

        assertEquals(
            listOf(DefinitionA, DefinitionB),
            manager.getAllDefinitions()
        )

        manager.definitionSources = listOf(
            SourceA.returning(DefinitionA),
            SourceB.returning(DefinitionB, DefinitionC /* appeared */),
        )

        assertEquals(
            listOf(DefinitionA, DefinitionB), /* still the same list */
            manager.getAllDefinitions()
        )

        manager.reloadScriptDefinitions()

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC /* now */),
            manager.getAllDefinitions()
        )
    }

    @Test
    fun `Reordering applied after reload`() {
        manager.definitionSources = listOf(
            SourceA.returning(DefinitionA),
            SourceB.returning(DefinitionB, DefinitionC),
        )

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC),
            manager.getAllDefinitions()
        )

        manager.settings = KotlinScriptingSettings(project).apply {
            setOrder(DefinitionC, 0)
            setOrder(DefinitionA, 1)
            setOrder(DefinitionB, 2)
        }

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC), /* no effect */
            manager.getAllDefinitions()
        )

        assertEquals(
            listOf(DefinitionC, DefinitionA, DefinitionB), /* now */
            manager.reloadScriptDefinitions()
        )
    }


    @Test
    fun `Deactivation affects only current definitions`() {
        manager.definitionSources = listOf(
            SourceA.returning(DefinitionA),
            SourceB.returning(DefinitionB, DefinitionC),
        )

        manager.settings = KotlinScriptingSettings(project).apply {
            setEnabled(DefinitionA, true)
            setEnabled(DefinitionB, false)
            setEnabled(DefinitionC, true)
        }

        assertEquals(
            listOf(DefinitionA, DefinitionB, DefinitionC),
            manager.getAllDefinitions()
        )

        assertEquals(
            listOf(DefinitionA, DefinitionC),
            manager.currentDefinitions.toList()
        )
    }

    @Test
    fun `First matching definition provided at search`() {
        val definitionA = TestDefinition("A") { false }
        val definitionB = TestDefinition("B") { false }
        val definitionC = TestDefinition("C") { true }
        val definitionD = TestDefinition("D") { true }

        val script = object : SourceCode {
            override val locationId: String = "script.kts"
            override val name: String = ""
            override val text: String = "println()"
        }

        manager.definitionSources = listOf(
            TestSource().returning(definitionA, definitionB, definitionC, definitionD)
        )

        assertTrue(manager.findDefinition(script)?.name == "C")
    }

    @Test
    fun `None of non-matching definitions is provided at search`() {
        val definitionA = TestDefinition("A") { false }
        val definitionB = TestDefinition("B") { false }

        val script = object : SourceCode {
            override val locationId: String = "script.kts"
            override val name: String = ""
            override val text: String = "println()"
        }

        manager.definitionSources = listOf(
            TestSource().returning(definitionA, definitionB)
        )

        assertNull(manager.findDefinition(script))
    }


    @Test
    fun `Definition order plays role at search`() {
        val definitionA = TestDefinition("A") { true }
        val definitionB = TestDefinition("B") { true }

        val script = object : SourceCode {
            override val locationId: String = "script.kts"
            override val name: String = ""
            override val text: String = "println()"
        }

        manager.definitionSources = listOf(
            /* both match */
            SourceA.returning(definitionA),
            SourceB.returning(definitionB),
        )

        assertTrue(manager.findDefinition(script)?.name == "A")

        manager.settings = KotlinScriptingSettings(project).apply {
            setOrder(definitionB, 0)
            setOrder(definitionA, 1)
        }

        manager.reorderScriptDefinitions()

        assertTrue(manager.findDefinition(script)?.name == "B")
    }

    @Test
    fun `Default definition is available via both new and legacy API`() {
        val defaultDefinition = manager.getDefaultDefinition()
        val defaultScriptDefinition = manager.getDefaultScriptDefinition()

        assertNotNull(defaultDefinition.asLegacyOrNull<BundledIdeScriptDefinition>())
        assertInstanceOf(BundledIdeScriptDefinition::class.java, defaultScriptDefinition)
    }


    @Test
    fun `Provided file extensions match known definitions`() {
        manager.definitionSources = listOf(
            SourceA.returning(TestDefinition("A", "a.kts"), TestDefinition("AA", "aa.kts")),
            SourceB.returning(TestDefinition("B", "b.kts")),
        )

        val extensions = manager.getKnownFilenameExtensions()
        extensions.toList() // see KT-63462
        assertEquals(listOf("a.kts", "aa.kts", "b.kts"), extensions.toList())
    }

    @Test
    fun `Matching definition defines whether the code is script`() {

        manager.definitionSources = listOf(
            SourceA.returning(TestDefinition("A") { true })
        )

        val script = object : SourceCode {
            override val locationId: String = "script.kts"
            override val name: String = ""
            override val text: String = "println()"
        }

        assertTrue(manager.isScript(script))

        manager.definitionSources = listOf(
            SourceA.returning(TestDefinition("A") { false }),
            SourceB.returning(TestDefinition("B") { false })
        )
        manager.reloadScriptDefinitions()

        assertFalse(manager.isScript(script))
    }
}

private class ScriptDefinitionsManagerUnderTest(val project: Project) : ScriptDefinitionsManager(project) {
    var definitionSources: List<ScriptDefinitionsSource> = emptyList()
    var settings: KotlinScriptingSettings = KotlinScriptingSettings(project)

    override fun getSources(): List<ScriptDefinitionsSource> = definitionSources

    override fun kotlinScriptingSettingsSafe(): KotlinScriptingSettings = settings

    override fun applyDefinitionsUpdate() {}

    override fun tryGetScriptDefinitionFast(locationId: String): ScriptDefinition? = null

    override fun isScratchFile(script: SourceCode): Boolean = false

    override fun getBundledScriptDefinitionContributor(): BundledScriptDefinitionContributor {
        return BundledScriptDefinitionContributor(project)
    }
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
