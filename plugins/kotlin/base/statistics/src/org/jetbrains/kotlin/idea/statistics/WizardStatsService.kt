// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import kotlin.math.abs
import kotlin.random.Random

interface WizardStats {
    fun toPairs(): ArrayList<EventPair<*>>
}

class WizardStatsService : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    companion object {

        // Collector ID
        private val GROUP = EventLogGroup("kotlin.ide.new.project", 11)

        // Whitelisted values for the events fields
        private val allowedProjectTemplates = listOf( // Modules
            "JVM_|_IDEA",
            "JS_|_IDEA",
            // Java and Gradle groups
            "Kotlin/JVM",
            // Gradle group
            "Kotlin/JS",
            "Kotlin/JS_for_browser",
            "Kotlin/JS_for_Node.js",
            "Kotlin/Multiplatform_as_framework",
            "Kotlin/Multiplatform",
            // Kotlin group
            "backendApplication",
            "consoleApplication",
            "multiplatformMobileApplication",
            "multiplatformMobileLibrary",
            "multiplatformApplication",
            "multiplatformLibrary",
            "nativeApplication",
            "frontendApplication",
            "fullStackWebApplication",
            "nodejsApplication",
            "reactApplication",
            "simpleWasmApplication",
            "none",
            // AppCode KMM
            "multiplatformMobileApplicationUsingAppleGradlePlugin",
            "multiplatformMobileApplicationUsingHybridProject",
        )
        private val allowedModuleTemplates = listOf(
            "consoleJvmApp",
            "ktorServer",
            "mobileMppModule",
            "nativeConsoleApp",
            "reactJsClient",
            "simpleJsClient",
            "simpleNodeJs",
            "simpleWasmClient",
            "none",
        )

        private val allowedWizardsGroups = listOf("Java", "Kotlin", "Gradle")
        private val allowedBuildSystems = listOf(
            "gradleKotlin",
            "gradleGroovy",
            "jps",
            "maven"
        )

        private val settings = Settings(
            SettingIdWithPossibleValues.Enum(
                id = "buildSystem.type",
                values = listOf(
                    "GradleKotlinDsl",
                    "GradleGroovyDsl",
                    "Jps",
                    "Maven",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "testFramework",
                values = listOf(
                    "NONE",
                    "JUNIT4",
                    "JUNIT5",
                    "TEST_NG",
                    "JS",
                    "COMMON",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "targetJvmVersion",
                values = listOf(
                    "JVM_1_6",
                    "JVM_1_8",
                    "JVM_9",
                    "JVM_10",
                    "JVM_11",
                    "JVM_12",
                    "JVM_13",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "androidPlugin",
                values = listOf(
                    "APPLICATION",
                    "LIBRARY",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "serverEngine",
                values = listOf(
                    "Netty",
                    "Tomcat",
                    "Jetty",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "kind",
                idToLog = "js.project.kind",
                values = listOf(
                    "LIBRARY",
                    "APPLICATION",
                )
            ),

            SettingIdWithPossibleValues.Enum(
                id = "compiler",
                idToLog = "js.compiler",
                values = listOf(
                    "IR",
                    "LEGACY",
                    "BOTH",
                )
            ),
            SettingIdWithPossibleValues.Enum(
                id = "projectTemplates.template",
                values = allowedProjectTemplates
            ),
            SettingIdWithPossibleValues.Enum(
                id = "module.template",
                values = allowedModuleTemplates
            ),
            SettingIdWithPossibleValues.Enum(
                id = "buildSystem.type",
                values = allowedBuildSystems
            ),

            SettingIdWithPossibleValues.Boolean(
                id = "javaSupport",
                idToLog = "jvm.javaSupport"
            ),
            SettingIdWithPossibleValues.Boolean(
                id = "cssSupport",
                idToLog = "js.cssSupport"
            ),
            SettingIdWithPossibleValues.Boolean(
                id = "useReactRouterDom",
                idToLog = "js.useReactRouterDom"
            ),
            SettingIdWithPossibleValues.Boolean(
                id = "useReactRedux",
                idToLog = "js.useReactRedux"
            ),
        )

        private val allowedModuleTypes = listOf(
            "androidNativeArm32Target",
            "androidNativeArm64Target",
            "iosArm32Target",
            "iosArm64Target",
            "iosX64Target",
            "iosTarget",
            "linuxArm32HfpTarget",
            "linuxMips32Target",
            "linuxMipsel32Target",
            "linuxX64Target",
            "macosX64Target",
            "mingwX64Target",
            "mingwX86Target",
            "nativeForCurrentSystem",
            "jsBrowser",
            "jsNode",
            "commonTarget",
            "jvmTarget",
            "androidTarget",
            "multiplatform",
            "JVM_Module",
            "android",
            "IOS_Module",
            "jsBrowserSinglePlatform",
            "jsNodeSinglePlatform",
            "wasmSimple",
        )


        private val settingIdField = EventFields.String("setting_id", settings.allowedIds)
        private val settingValueField = EventFields.String("setting_value", settings.possibleValues)

        // Event fields
        val groupField = EventFields.String("group", allowedWizardsGroups)
        val projectTemplateField = EventFields.String("project_template", allowedProjectTemplates)
        val buildSystemField = EventFields.String("build_system", allowedBuildSystems)

        val modulesCreatedField = EventFields.Int("modules_created")
        val modulesRemovedField = EventFields.Int("modules_removed")
        val moduleTemplateChangedField = EventFields.Int("module_template_changed")

        private val moduleTemplateField = EventFields.String("module_template", allowedModuleTemplates)
        private val sessionIdField = EventFields.Int("session_id")

        val modulesListField = StringListEventField.ValidatedByAllowedValues("project_modules_list", allowedModuleTypes)

        private val moduleTypeField = EventFields.String("module_type", allowedModuleTypes)

        private val pluginInfoField = EventFields.PluginInfo.with(getPluginInfoById(KotlinIdePlugin.id))

        // Events
        private val projectCreatedEvent = GROUP.registerVarargEvent(
            "project_created",
            groupField,
            projectTemplateField,
            buildSystemField,
            modulesCreatedField,
            modulesRemovedField,
            moduleTemplateChangedField,
            modulesListField,
            sessionIdField,
            EventFields.PluginInfo
        )

        private val projectOpenedByHyperlinkEvent = GROUP.registerVarargEvent(
            "wizard_opened_by_hyperlink",
            projectTemplateField,
            sessionIdField,
            EventFields.PluginInfo
        )

        private val moduleTemplateCreatedEvent = GROUP.registerVarargEvent(
            "module_template_created",
            projectTemplateField,
            moduleTemplateField,
            sessionIdField,
            EventFields.PluginInfo
        )

        private val settingValueChangedEvent = GROUP.registerVarargEvent(
            "setting_value_changed",
            settingIdField,
            settingValueField,
            sessionIdField,
            EventFields.PluginInfo,
        )

        private val jdkChangedEvent = GROUP.registerVarargEvent(
            "jdk_changed",
            sessionIdField,
            EventFields.PluginInfo,
        )

        private val nextClickedEvent = GROUP.registerVarargEvent(
            "next_clicked",
            sessionIdField,
            EventFields.PluginInfo,
        )

        private val prevClickedEvent = GROUP.registerVarargEvent(
            "prev_clicked",
            sessionIdField,
            EventFields.PluginInfo,
        )

        private val moduleCreatedEvent = GROUP.registerVarargEvent(
            "module_created",
            moduleTypeField,
            sessionIdField,
            EventFields.PluginInfo,
        )

        private val moduleRemovedEvent = GROUP.registerVarargEvent(
            "module_removed",
            moduleTypeField,
            sessionIdField,
            EventFields.PluginInfo,
        )

        // Log functions
        fun logDataOnProjectGenerated(session: WizardLoggingSession?, project: Project?, projectCreationStats: ProjectCreationStats) {
            projectCreatedEvent.log(
                project,
                *projectCreationStats.toPairs().toTypedArray(),
                *session?.let { arrayOf(sessionIdField with it.id) }.orEmpty(),
                pluginInfoField
            )
        }

        fun logDataOnSettingValueChanged(
            session: WizardLoggingSession,
            settingId: String,
            settingValue: String
        ) {
            val idToLog = settings.getIdToLog(settingId) ?: return
            settingValueChangedEvent.log(
                settingIdField with idToLog,
                settingValueField with settingValue,
                sessionIdField with session.id,
                pluginInfoField,
            )
        }

        fun logDataOnJdkChanged(
            session: WizardLoggingSession,
        ) {
            jdkChangedEvent.log(
                sessionIdField with session.id,
                pluginInfoField,
            )
        }

        fun logDataOnNextClicked(
            session: WizardLoggingSession,
        ) {
            nextClickedEvent.log(
                sessionIdField with session.id,
                pluginInfoField,
            )
        }

        fun logDataOnPrevClicked(
            session: WizardLoggingSession,
        ) {
            prevClickedEvent.log(
                sessionIdField with session.id,
                pluginInfoField,
            )
        }

        fun logOnModuleCreated(
            session: WizardLoggingSession,
            moduleType: String,
        ) {
            moduleCreatedEvent.log(
                sessionIdField with session.id,
                moduleTypeField with moduleType.withSpacesRemoved(),
                pluginInfoField,
            )
        }

        fun logOnModuleRemoved(
            session: WizardLoggingSession,
            moduleType: String,
        ) {
            moduleRemovedEvent.log(
                sessionIdField with session.id,
                moduleTypeField with moduleType.withSpacesRemoved(),
                pluginInfoField,
            )
        }


        fun logDataOnProjectGenerated(
            session: WizardLoggingSession?,
            project: Project?,
            projectCreationStats: ProjectCreationStats,
            uiEditorUsageStats: UiEditorUsageStats
        ) {
            projectCreatedEvent.log(
                project,
                *projectCreationStats.toPairs().toTypedArray(),
                *uiEditorUsageStats.toPairs().toTypedArray(),
                *session?.let { arrayOf(sessionIdField with it.id) }.orEmpty(),
                pluginInfoField
            )
        }

        fun logUsedModuleTemplatesOnNewWizardProjectCreated(
            session: WizardLoggingSession,
            project: Project?,
            projectTemplateId: String,
            moduleTemplates: List<String>
        ) {
            moduleTemplates.forEach { moduleTemplateId ->
                logModuleTemplateCreation(session, project, projectTemplateId, moduleTemplateId)
            }
        }

        fun logWizardOpenByHyperlink(session: WizardLoggingSession, project: Project?, templateId: String?) {
            projectOpenedByHyperlinkEvent.log(
                project,
                projectTemplateField.with(templateId ?: "none"),
                sessionIdField with session.id,
                pluginInfoField
            )
        }

        private fun logModuleTemplateCreation(
            session: WizardLoggingSession,
            project: Project?,
            projectTemplateId: String,
            moduleTemplateId: String
        ) {
            moduleTemplateCreatedEvent.log(
                project,
                projectTemplateField.with(projectTemplateId),
                moduleTemplateField.with(moduleTemplateId),
                sessionIdField with session.id,
                pluginInfoField
            )
        }
    }

    data class ProjectCreationStats(
        val group: String,
        val projectTemplateId: String,
        val buildSystemType: String,
        val moduleTypes: List<String> = emptyList(),
    ) : WizardStats {
        override fun toPairs(): ArrayList<EventPair<*>> = arrayListOf(
            groupField.with(group),
            projectTemplateField.with(projectTemplateId),
            buildSystemField.with(buildSystemType),
            modulesListField with moduleTypes,
        )
    }

    data class UiEditorUsageStats(
        var modulesCreated: Int = 0,
        var modulesRemoved: Int = 0,
        var moduleTemplateChanged: Int = 0
    ) : WizardStats {
        override fun toPairs(): ArrayList<EventPair<*>> = arrayListOf(
            modulesCreatedField.with(modulesCreated),
            modulesRemovedField.with(modulesRemoved),
            moduleTemplateChangedField.with(moduleTemplateChanged)
        )
    }
}

private fun String.withSpacesRemoved(): String =
    replace(' ', '_')

private sealed class SettingIdWithPossibleValues {
    abstract val id: String
    abstract val idToLog: String
    abstract val values: List<String>

    data class Enum(
        override val id: String,
        override val idToLog: String = id,
        override val values: List<String>
    ) : SettingIdWithPossibleValues()

    data class Boolean(
        override val id: String,
        override val idToLog: String = id,
    ) : SettingIdWithPossibleValues() {
        override val values: List<String> get() = listOf(true.toString(), false.toString())
    }
}

private class Settings(settingIdWithPossibleValues: List<SettingIdWithPossibleValues>) {
    constructor(vararg settingIdWithPossibleValues: SettingIdWithPossibleValues) : this(settingIdWithPossibleValues.toList())

    val allowedIds = settingIdWithPossibleValues.map { it.idToLog }
    val possibleValues = settingIdWithPossibleValues.flatMap { it.values }.distinct()
    private val id2IdToLog = settingIdWithPossibleValues.associate { it.id to it.idToLog }

    fun getIdToLog(id: String): String? = id2IdToLog.get(id)
}

class WizardLoggingSession private constructor(val id: Int) {
    companion object {
        fun createWithRandomId(): WizardLoggingSession =
            WizardLoggingSession(id = abs(Random.nextInt()))
    }
}