// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.settings

import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionDiscoverySource
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class ScriptDefinitionsSnapshotTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        clearSettings()
    }

    override fun tearDown() {
        RunAll(
            ThrowableRunnable { clearSettings() },
            ThrowableRunnable { super.tearDown() },
        ).run()
    }

    fun `test a row carries the whole definition and its enabled state`() {
        val item = row("Sample")
        val table = ScriptDefinitionTable(mutableListOf(item))

        assertEquals("A row is a summary and a checkbox, not a column per field", 2, table.columnCount)
        assertEquals(item, table.getValueAt(0, 0))
        assertEquals(true, table.getValueAt(0, 1))
    }

    fun `test the second line of a row holds the file pattern and the source`() {
        val detail = row("Sample").detailText

        assertTrue("The second line must name the pattern, was '$detail'", detail.contains(".sample.kts"))
        assertTrue("The second line must name the origin, was '$detail'", detail.contains("Marker file"))
    }

    fun `test a source line names the origin and its location`() {
        val plugin = ScriptDefinitionDiscoverySource.Provider("com.example.SomeProvider", "Kotlin")
        assertEquals("Plugin", plugin.originText)
        assertEquals("Kotlin", plugin.locationText)

        val marker = ScriptDefinitionDiscoverySource.MarkerFile(jarPath = "/libs/mcp-kts.jar")
        assertEquals("Marker file", marker.originText)
        assertEquals("/libs/mcp-kts.jar", marker.locationText)

        val manual = ScriptDefinitionDiscoverySource.Manual
        assertEquals("Manual Settings", manual.originText)
        assertEquals("A manual row must not name a JAR it cannot attribute", "", manual.locationText)

        assertEquals("", null.originText)
        assertEquals("", null.locationText)
    }

    fun `test a definition that cannot be switched off keeps a disabled checkbox`() {
        val table = ScriptDefinitionTable(mutableListOf(row("Fixed", canBeSwitchedOff = false), row("Free")))

        val fixed = table.items.indexOfFirst { it.name == "Fixed" }
        val free = table.items.indexOfFirst { it.name == "Free" }

        assertFalse("A definition that cannot be switched off must not be editable", table.isCellEditable(fixed, 1))
        assertTrue("A switchable definition must stay editable", table.isCellEditable(free, 1))
    }

    fun `test apply persists order, enabled state, class names and classpath`() {
        val snapshot = ScriptDefinitionsSnapshot(
            definitions = listOf(row("First"), row("Second", enabled = false)),
            definitionClasses = listOf("com.example.A", "com.example.B"),
            classpath = listOf("/libs/my libs/a.jar", """C:\libs\b.jar"""),
        )

        val state = snapshot.toSettingsState()

        assertEquals(listOf("First", "Second"), state.settings.map { it.name })
        assertEquals(listOf("com.example.First", "com.example.Second"), state.settings.map { it.definitionId })
        assertEquals(listOf(true, false), state.settings.map { it.enabled })
        assertEquals(listOf("com.example.A", "com.example.B"), state.parsedClassNames)
        assertEquals(
            "A path holding a space or a drive letter must survive the round trip",
            listOf("/libs/my libs/a.jar", """C:\libs\b.jar"""),
            state.parsedClasspath,
        )
    }

    fun `test reset reads the persisted class names and classpath back`() {
        KotlinScriptingSettings.getInstance(project).update {
            it.copy(
                explicitTemplateClassNames = "com.example.A\ncom.example.B",
                explicitTemplateClasspath = "/libs/my libs/a.jar",
            )
        }

        val snapshot = readScriptDefinitionsSnapshot(project)

        assertEquals(listOf("com.example.A", "com.example.B"), snapshot.definitionClasses)
        assertEquals(listOf("/libs/my libs/a.jar"), snapshot.classpath)
    }

    fun `test list order survives reset, an edit and apply`() {
        KotlinScriptingSettings.getInstance(project).update {
            it.copy(explicitTemplateClassNames = "com.example.A\ncom.example.B\ncom.example.C")
        }

        val edited = readScriptDefinitionsSnapshot(project).let {
            it.copy(definitionClasses = it.definitionClasses + "com.example.D")
        }
        KotlinScriptingSettings.getInstance(project).update { edited.toSettingsState() }

        assertEquals(
            listOf("com.example.A", "com.example.B", "com.example.C", "com.example.D"),
            readScriptDefinitionsSnapshot(project).definitionClasses,
        )
    }

    fun `test a detached snapshot compares equal until a row is edited`() {
        val applied = ScriptDefinitionsSnapshot(listOf(row("First")), listOf("com.example.A"), emptyList())
        val detached = applied.copy(definitions = applied.definitions.map { it.copy() })

        assertEquals("A clean form must not be reported as modified", applied, detached)

        detached.definitions.first().isEnabled = false
        assertFalse("Clearing the checkbox must be detected as a change", applied == detached)
    }

    fun `test the classpath parser splits on a line break only`() {
        assertEquals(
            listOf("/libs/my libs/a.jar", """C:\libs\b.jar"""),
            parseClasspathInput("/libs/my libs/a.jar\n\n  C:\\libs\\b.jar  "),
        )
    }

    fun `test the class name parser still splits on every delimiter`() {
        assertEquals(
            listOf("com.example.A", "com.example.B", "com.example.C"),
            parseExplicitTemplateInput("com.example.A com.example.B;com.example.C"),
        )
    }

    fun `test a marker file tooltip names its root and its marker`() {
        val source = ScriptDefinitionDiscoverySource.MarkerFile(
            rootName = "mcp-script-definition-0.1.0.jar",
            jarPath = "/Users/me/mcp/build/libs/mcp-script-definition-0.1.0.jar",
            displayLocation = "build/libs/mcp-script-definition-0.1.0.jar",
        )

        val rows = source.tooltipRows("com.example.Script").map { it.label to it.value }

        assertEquals(
            listOf(
                "Definition source:" to "mcp-script-definition-0.1.0.jar",
                "Marker file:" to "META-INF/kotlin/script/templates/com.example.Script.classname",
                "Definition class:" to "com.example.Script",
            ),
            rows,
        )
    }

    fun `test a marker file tooltip falls back to the location when no root names it`() {
        val source = ScriptDefinitionDiscoverySource.MarkerFile(displayLocation = "build/classes")

        val rows = source.tooltipRows("com.example.Script").associate { it.label to it.value }

        assertEquals("build/classes", rows["Definition source:"])
    }

    fun `test a provider tooltip names the extension class and the plugin id`() {
        val source = ScriptDefinitionDiscoverySource.Provider(
            implementationFqn = "org.jetbrains.kotlin.idea.core.script.definitions.MainKtsScriptDefinitionsProvider",
            pluginName = "Kotlin",
            pluginId = "org.jetbrains.kotlin",
        )

        val rows = source.tooltipRows("org.jetbrains.kotlin.mainKts.MainKtsScript").map { it.label to it.value }

        assertEquals(
            listOf(
                "Definition source:" to "org.jetbrains.kotlin.idea.core.script.definitions.MainKtsScriptDefinitionsProvider",
                "Plugin ID:" to "org.jetbrains.kotlin",
                "Definition class:" to "org.jetbrains.kotlin.mainKts.MainKtsScript",
            ),
            rows,
        )
    }

    fun `test a manual tooltip names the settings and no JAR`() {
        val rows = ScriptDefinitionDiscoverySource.Manual.tooltipRows("com.example.Manual").map { it.label to it.value }

        assertEquals(
            listOf("Definition source:" to "Manual Settings", "Definition class:" to "com.example.Manual"),
            rows,
        )
    }

    fun `test a path pattern reaches the tooltip, because no row can show it`() {
        val pattern = """^.*\\.settings\\.gradle\\.kts$"""
        val rows = ScriptDefinitionDiscoverySource.Manual.tooltipRows("com.example.Gradle", pattern)
            .associate { it.label to it.value }

        assertEquals(pattern, rows["File pattern:"])
        assertNull("A definition with no path pattern must not get the row", 
                   ScriptDefinitionDiscoverySource.Manual.tooltipRows("com.example.Gradle").associate { it.label to it.value }["File pattern:"])
    }

    fun `test the tooltip renders a two column table`() {
        val html = listOf(
            TooltipRow("Plugin", "Kotlin"),
            TooltipRow("Definition class", "com.example.Script", code = true),
        ).toTooltipHtml().toString()

        assertTrue("A label must be bold, was '$html'", html.contains("<b>Plugin</b>"))
        assertTrue("A label must stay top aligned, was '$html'", html.contains("valign=\"top\""))
        assertTrue("A class name must render as code, was '$html'", html.contains("<code>"))
    }

    fun `test only a value too long to fit gets a break after each separator`() {
        val short = "com.example.Script"
        val long = "org.jetbrains.kotlin.idea.core.script.definitions.MainKtsScriptDefinitionsProviderLong"

        val html = listOf(TooltipRow("Short", short), TooltipRow("Long", long)).toTooltipHtml().toString()

        // A break is a real character, so a value that fits must stay the value itself.
        assertTrue("A short value must stay literal, was '$html'", html.contains(short))
        assertFalse("A short value must hold no zero-width space, was '$html'", html.contains("com.\u200bexample"))
        assertTrue("A long value must break after each dot, was '$html'", html.contains("org.\u200bjetbrains.\u200bkotlin"))
    }

    private fun clearSettings() {
        KotlinScriptingSettings.getInstance(project).update { KotlinScriptingSettings.State() }
    }

    private fun row(name: String, enabled: Boolean = true, canBeSwitchedOff: Boolean = true) = ScriptDefinitionTableModel(
        id = "com.example.$name",
        name = name,
        pattern = ".sample.kts",
        filePattern = null,
        source = ScriptDefinitionDiscoverySource.MarkerFile(rootName = "com.example.$name.jar"),
        canBeSwitchedOff = canBeSwitchedOff,
        isEnabled = enabled,
    )
}
