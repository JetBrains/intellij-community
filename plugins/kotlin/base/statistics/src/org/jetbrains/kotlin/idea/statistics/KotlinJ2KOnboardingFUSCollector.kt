// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.math.absoluteValue
import kotlin.random.Random

class KotlinJ2KOnboardingImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        // This is seemingly only called for Gradle projects, but even if it is called from Maven projects too,
        // it should not break anything.
        KotlinJ2KOnboardingFUSCollector.logProjectSyncCompleted(project)
    }
}

object KotlinJ2KOnboardingFUSCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    val GROUP: EventLogGroup = EventLogGroup("kotlin.onboarding.j2k", 2)

    internal val pluginVersion = getPluginInfoById(KotlinIdePlugin.id).version
    internal val buildSystemField = EventFields.Enum<KotlinJ2KOnboardingBuildSystem>("build_system")
    internal val buildSystemVersionField = EventFields.StringValidatedByRegexpReference("build_system_version", "version")
    internal val sessionIdField = EventFields.Int("onboarding_session_id")
    internal val canAutoConfigureField = EventFields.Boolean("can_auto_configure")
    internal val isAutoConfigurationField = EventFields.Boolean("is_auto_configuration")

    private val commonFields = arrayOf(
        sessionIdField, buildSystemField, buildSystemVersionField, isAutoConfigurationField, EventFields.Version
    )
    private val autoConfigFields = commonFields.toList() + canAutoConfigureField
    private val startProjectSyncFields = commonFields.toList() + isAutoConfigurationField

    private val openFirstKtFileDialog = GROUP.registerVarargEvent("first_kt_file.dialog_opened", *commonFields)
    private val createFirstKtFile = GROUP.registerVarargEvent("first_kt_file.created", *commonFields)
    private val autoConfigStatusChecked = GROUP.registerVarargEvent("auto_config.checked", *autoConfigFields.toTypedArray())
    private val showConfigureKtPanel = GROUP.registerVarargEvent("configure_kt_panel.shown", *commonFields)
    private val showConfigureKtNotification = GROUP.registerVarargEvent("configure_kt_notification.shown", *commonFields)
    private val clickConfigureKtNotification = GROUP.registerVarargEvent("configure_kt_notification.clicked", *commonFields)
    private val showConfigureKtWindow = GROUP.registerVarargEvent("configure_kt_window.shown", *commonFields)
    private val startConfigureKt = GROUP.registerVarargEvent("configure_kt.started", *commonFields)
    private val showConfiguredKtNotification = GROUP.registerVarargEvent("configured_kt_notification.shown", *commonFields)
    private val startProjectSync = GROUP.registerVarargEvent("project_sync.started", *startProjectSyncFields.toTypedArray())
    private val failedProjectSync = GROUP.registerVarargEvent("project_sync.failed", *commonFields)
    private val completeProjectSync = GROUP.registerVarargEvent("project_sync.completed", *commonFields)
    private val undoConfigureKotlin = GROUP.registerVarargEvent("configure_kt.undone", *commonFields)

    private fun KotlinOnboardingSession.log(eventId: VarargEventId, vararg pairs: EventPair<*>) {
        eventId.log(getPairs() + pairs)
    }

    private fun Project.runEventLogger(runnable: suspend KotlinOnboardingJ2KSessionService.() -> Unit) {
        if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) {
            // No statistic collection allowed -> no point running the event logger
            return
        }
        val service = serviceOrNull<KotlinOnboardingJ2KSessionService>() ?: return
        // We are currently only interested in Gradle and Maven projects
        if (service.determineBuildSystem() != KotlinJ2KOnboardingBuildSystem.GRADLE &&
            service.determineBuildSystem() != KotlinJ2KOnboardingBuildSystem.MAVEN
        ) {
            return
        }
        service.runEventLogger(runnable)
    }

    fun logKtFileDialogOpened(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin() || hasKotlinFiles()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(openFirstKtFileDialog)
    }

    fun logFirstKtFileCreated(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin() || hasKotlinFiles()) return@runEventLogger
        markFirstKotlinFileCreated()
        val session = getOrCreateSession()
        session.log(createFirstKtFile)
    }

    fun logCheckAutoConfigStatus(project: Project, canAutoConfigure: Boolean): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(autoConfigStatusChecked, canAutoConfigureField.with(canAutoConfigure))
    }

    fun logShowConfigureKtPanel(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(showConfigureKtPanel)
    }

    fun logShowConfigureKtNotification(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(showConfigureKtNotification)
    }

    fun logClickConfigureKtNotification(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(clickConfigureKtNotification)
    }

    @JvmStatic
    fun logShowConfigureKtWindow(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(showConfigureKtWindow)
    }

    fun logStartConfigureKt(project: Project, isAutoConfiguration: Boolean = false): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(startConfigureKt, isAutoConfigurationField.with(isAutoConfiguration))
    }

    fun logShowConfiguredKtNotification(project: Project): Unit = project.runEventLogger {
        if (hasKotlinPlugin()) return@runEventLogger
        val session = getOrCreateSession()
        session.log(showConfiguredKtNotification)
    }

    fun logProjectSyncStarted(
        project: Project,
        modulesWereLoadedBefore: Boolean
    ): Unit = project.runEventLogger {
        val hasKotlinPlugin = hasKotlinPlugin()
        // If the modules were not loaded before, we have no information if the kotlin plugin
        // was already configured prior to the sync
        kotlinConfiguredBeforeSync = if (modulesWereLoadedBefore) hasKotlinPlugin else null
        // Do not start a new session here
        val session = openSession ?: return@runEventLogger
        session.log(startProjectSync)
    }

    fun logProjectSyncCompleted(project: Project): Unit = project.runEventLogger {
        val wasConfiguredBeforeSync = kotlinConfiguredBeforeSync
        kotlinConfiguredBeforeSync = null
        val hasKotlinPlugin = hasKotlinPlugin(useCache = false)
        if (!hasKotlinPlugin) return@runEventLogger

        openSession?.let { session ->
            session.log(completeProjectSync)
            endSession(true)
            return@runEventLogger
        }

        // A null value indicates that we did not pass the start sync callback beforehand,
        // which means this was likely a fresh project import.
        // If the value is true, then we did not add the KT plugin because it was already added before.
        if (wasConfiguredBeforeSync == false) {
            val session = getOrCreateSession()
            session.log(completeProjectSync)
            endSession(true)
        }
    }

    fun logConfigureKtUndone(project: Project): Unit = project.runEventLogger {
        val session = openSession ?: lastSuccessfullyCompletedSession ?: return@runEventLogger
        session.log(undoConfigureKotlin)
    }
}


/**
 * A session of the J2K onboarding process.
 * This session was initially started and is kept open until
 * the Kotlin plugin was added and synced correctly.
 */
internal data class KotlinOnboardingSession(
    val id: Int,
    val buildSystemType: KotlinJ2KOnboardingBuildSystem,
    val buildSystemVersion: String?
) {
    fun getPairs(): List<EventPair<*>> {
        return mutableListOf<EventPair<*>>().apply {
            add(KotlinJ2KOnboardingFUSCollector.sessionIdField.with(id))
            add(KotlinJ2KOnboardingFUSCollector.buildSystemField.with(buildSystemType))
            buildSystemVersion?.let {
                add(KotlinJ2KOnboardingFUSCollector.buildSystemVersionField.with(buildSystemVersion))
            }
            add(EventFields.Version.with(KotlinJ2KOnboardingFUSCollector.pluginVersion))
        }
    }
}

@Service(Service.Level.PROJECT)
class KotlinOnboardingJ2KSessionService(private val project: Project, private val coroutineScope: CoroutineScope) {
    private var hasKotlinPlugin: Boolean? = null
    private var hasKotlinFile: Boolean? = null

    internal var openSession: KotlinOnboardingSession? = null
        private set

    internal var lastSuccessfullyCompletedSession: KotlinOnboardingSession? = null
        private set

    internal var kotlinConfiguredBeforeSync: Boolean? = null

    private val sessionMutex: Mutex = Mutex()

    /**
     * Some of the functions required by this FUS might require scanning the project and thus
     * could take some time. To prevent the IDE's UI thread from stuttering we do these calculations
     * asynchronously inside the [runnable].
     * The [runnable] is protected by a mutex guaranteeing at most one event can be handled at the same time,
     * which also prevents synchronization issues with members of this companion object.
     * It is guaranteed that the [runnable]s are executed in the same order as they are submitted.
     */
    internal fun runEventLogger(runnable: suspend KotlinOnboardingJ2KSessionService.() -> Unit) {
        coroutineScope.launch {
            // This (almost) ensures that we have a FIFO behaviour of events.
            sessionMutex.withLock {
                runnable()
            }
        }
    }

    private fun BuildSystemType.getType(): KotlinJ2KOnboardingBuildSystem {
        return when (this) {
            BuildSystemType.Gradle -> KotlinJ2KOnboardingBuildSystem.GRADLE
            BuildSystemType.AndroidGradle -> KotlinJ2KOnboardingBuildSystem.GRADLE
            BuildSystemType.Maven -> KotlinJ2KOnboardingBuildSystem.MAVEN
            BuildSystemType.JPS -> KotlinJ2KOnboardingBuildSystem.JPS
            else -> KotlinJ2KOnboardingBuildSystem.UNKNOWN
        }
    }

    private var cachedBuildSystem: KotlinJ2KOnboardingBuildSystem? = null

    internal fun determineBuildSystem(): KotlinJ2KOnboardingBuildSystem {
        cachedBuildSystem?.let { return it }
        val allBuildSystems = project.modules.map { it.buildSystemType.getType() }.toSet()
        if (allBuildSystems.size > 1) return KotlinJ2KOnboardingBuildSystem.MULTIPLE
        val buildSystem = allBuildSystems.singleOrNull()
        cachedBuildSystem = buildSystem
        return buildSystem ?: KotlinJ2KOnboardingBuildSystem.UNKNOWN
    }

    private suspend fun getGradleVersion(): String? {
        return null
        /* cannot be done at this time due to dependency issues
        return readAction {
            val gradleSettings = GradleSettings.getInstance(project)
            project.guessProjectDir()?.path?.let {
                val linkedSettings = gradleSettings.getLinkedProjectSettings(it)
                linkedSettings?.resolveGradleVersion()?.version
            }
        }*/
    }

    internal suspend fun getOrCreateSession(): KotlinOnboardingSession {
        val existingSession = openSession
        if (existingSession != null) return existingSession
        val buildSystem = determineBuildSystem()
        val buildSystemVersion = if (buildSystem == KotlinJ2KOnboardingBuildSystem.GRADLE) getGradleVersion() else null
        val newSession = KotlinOnboardingSession(
            id = Random.nextInt().absoluteValue,
            buildSystemType = buildSystem,
            buildSystemVersion = buildSystemVersion
        )
        openSession = newSession
        lastSuccessfullyCompletedSession = null
        return newSession
    }

    internal fun markFirstKotlinFileCreated() {
        hasKotlinFile = true
    }

    /**
     * Caches if the project contains a Kotlin file.
     */
    @OptIn(UnsafeCastFunction::class)
    internal suspend fun hasKotlinFiles(): Boolean {
        hasKotlinFile?.let { return it }
        return readAction {
            // Duplicated from Project.containsNonScriptKotlinFile to avoid cyclical dependencies
            val result = !FileTypeIndex.processFiles(
                KotlinFileType.INSTANCE,
                {
                    val psiFile = PsiManager.getInstance(project).findFile(it)
                    psiFile?.safeAs<KtFile>()?.isScript() != false
                },
                GlobalSearchScope.projectScope(project),
            )
            hasKotlinFile = result
            result
        }
    }

    private fun Module.hasKotlinPluginEnabled(): Boolean {
        val settings = KotlinFacetSettingsProvider.getInstance(project)
        val moduleSettings = settings?.getSettings(this) ?: return false
        return moduleSettings.compilerSettings != null
    }

    /**
     * Caches if the project has Kotlin enabled in any submodule.
     */
    internal suspend fun hasKotlinPlugin(useCache: Boolean = true): Boolean {
        val existingValue = hasKotlinPlugin
        if (useCache && existingValue != null) {
            return existingValue
        }
        project.waitForSmartMode()
        val newValue = readAction {
            project.modules.any { it.hasKotlinPluginEnabled() }
        }
        hasKotlinPlugin = newValue
        return newValue
    }

    internal fun endSession(success: Boolean) {
        if (success) {
            lastSuccessfullyCompletedSession = openSession
        }
        openSession = null
    }
}

internal enum class KotlinJ2KOnboardingBuildSystem {
    GRADLE, MAVEN, MULTIPLE, UNKNOWN, JPS
}