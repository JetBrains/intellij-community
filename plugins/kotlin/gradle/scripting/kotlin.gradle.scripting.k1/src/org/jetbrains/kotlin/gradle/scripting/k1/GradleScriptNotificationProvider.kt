// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1

import KotlinGradleScriptingBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportProvider
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.gradle.scripting.shared.GradleStandaloneScriptActionsManager
import org.jetbrains.kotlin.gradle.scripting.shared.isGradleKotlinScript
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator.NotificationKind.*
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator.ScriptUnderRoot
import org.jetbrains.kotlin.gradle.scripting.shared.roots.Imported
import org.jetbrains.kotlin.gradle.scripting.shared.runPartialGradleImport
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.v1.hasUnknownScriptExt
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import javax.swing.JComponent

internal class GradleScriptNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): java.util.function.Function<in FileEditor, out JComponent?>? {
        if (!isGradleKotlinScript(file) || !file.isKotlinFileType()) {
            return null
        }

        val standaloneScriptActions = GradleStandaloneScriptActionsManager.getInstance(project)
        val rootsManager = GradleBuildRootsLocator.getInstance(project)
        val scriptUnderRoot = rootsManager.findScriptBuildRoot(file) ?: return null

        // todo: this actions will be usefull only when gradle fix https://github.com/gradle/gradle/issues/12640
        fun EditorNotificationPanel.showActionsToFixNotEvaluated() {
            // suggest to reimport project if something changed after import
            val build: Imported = scriptUnderRoot.nearest as? Imported ?: return
            val importTs = build.data.importTs
            if (!build.areRelatedFilesChangedBefore(file, importTs)) {
                createActionLabel(KotlinGradleScriptingBundle.message("action.LoadKtGradleConfiguration.text")) {
                    rootsManager.updateStandaloneScripts {
                      runPartialGradleImport(project, build)
                    }
                }
            }

            // suggest to choose new gradle project
            createActionLabel(KotlinGradleScriptingBundle.message("notification.outsideAnything.linkAction")) {
                linkProject(project, scriptUnderRoot)
            }
        }

        return java.util.function.Function { fileEditor ->
            when (scriptUnderRoot.notificationKind) {
                dontCare -> null
                legacy -> {
                    val actions = standaloneScriptActions[file]
                    if (actions == null) null
                    else {
                        object : EditorNotificationPanel(fileEditor, Status.Info) {
                            val contextHelp = KotlinGradleScriptingBundle.message("notification.gradle.legacy.firstLoad.info")

                            init {
                                if (actions.isFirstLoad) {
                                    text(KotlinGradleScriptingBundle.message("notification.gradle.legacy.firstLoad"))
                                    toolTipText = contextHelp
                                } else {
                                    text(KotlinGradleScriptingBundle.message("notification.text.script.configuration.has.been.changed"))
                                }

                                createActionLabel(KotlinGradleScriptingBundle.message("notification.action.text.load.script.configuration")) {
                                    actions.reload()
                                }

                                createActionLabel(KotlinBaseScriptingBundle.message("notification.action.text.enable.auto.reload")) {
                                    actions.enableAutoReload()
                                }

                                if (actions.isFirstLoad) {
                                    contextHelp(contextHelp)
                                }
                            }
                        }
                    }
                }
                legacyOutside -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.gradle.legacy.outsideProject"))
                    createActionLabel(KotlinGradleScriptingBundle.message("notification.notEvaluatedInLastImport.addAsStandaloneAction")) {
                        rootsManager.updateStandaloneScripts {
                            addStandaloneScript(file.path)
                        }
                    }
                    contextHelp(KotlinGradleScriptingBundle.message("notification.gradle.legacy.outsideProject.addToStandaloneHelp"))
                }
                outsideAnything -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.outsideAnything.text"))
                    createActionLabel(KotlinGradleScriptingBundle.message("notification.outsideAnything.linkAction")) {
                        linkProject(project, scriptUnderRoot)
                    }
                }
                wasNotImportedAfterCreation -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.wasNotImportedAfterCreation.text"))
                    createActionLabel(KotlinGradleScriptingBundle.message("action.LoadKtGradleConfiguration.text")) {
                        val root = scriptUnderRoot.nearest
                        if (root != null) {
                            runPartialGradleImport(project, root)
                        }
                    }
                    contextHelp(KotlinGradleScriptingBundle.message("notification.wasNotImportedAfterCreation.help"))
                }
                notEvaluatedInLastImport -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.notEvaluatedInLastImport.text"))

                    // todo: this actions will be usefull only when gradle fix https://github.com/gradle/gradle/issues/12640
                    // showActionsToFixNotEvaluated()

                    if (file.hasUnknownScriptExt()) {
                        createActionLabel(KotlinGradleScriptingBundle.message("notification.notEvaluatedInLastImport.addAsStandaloneAction")) {
                            rootsManager.updateStandaloneScripts {
                                addStandaloneScript(file.path)
                            }
                        }
                    }
                    contextHelp(KotlinGradleScriptingBundle.message("notification.notEvaluatedInLastImport.info"))
                }
                standalone, standaloneLegacy -> EditorNotificationPanel(fileEditor, Status.Info).apply {
                    val actions = standaloneScriptActions[file]
                    if (actions != null) {
                        text(
                            KotlinGradleScriptingBundle.message("notification.standalone.text") +
                                    ". " +
                                    KotlinGradleScriptingBundle.message("notification.text.script.configuration.has.been.changed")
                        )

                        createActionLabel(KotlinGradleScriptingBundle.message("notification.action.text.load.script.configuration")) {
                            actions.reload()
                        }

                        createActionLabel(KotlinBaseScriptingBundle.message("notification.action.text.enable.auto.reload")) {
                            actions.enableAutoReload()
                        }
                    } else {
                        text(KotlinGradleScriptingBundle.message("notification.standalone.text"))
                    }

                    createActionLabel(KotlinGradleScriptingBundle.message("notification.standalone.disableScriptAction")) {
                        rootsManager.updateStandaloneScripts {
                            removeStandaloneScript(file.path)
                        }
                    }

                    if (scriptUnderRoot.notificationKind == standaloneLegacy) {
                        contextHelp(KotlinGradleScriptingBundle.message("notification.gradle.legacy.standalone.info"))
                    } else {
                        contextHelp(KotlinGradleScriptingBundle.message("notification.standalone.info"))
                    }
                }
            }
        }
    }

    private fun linkProject(
      project: Project,
      scriptUnderRoot: ScriptUnderRoot,
    ) {
        val settingsFile: File? = tryFindGradleSettings(scriptUnderRoot)

        // from AttachExternalProjectAction

        val manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID) ?: return
        val provider = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.extensionList.find {
          it is AbstractExternalProjectImportProvider && GradleConstants.SYSTEM_ID == it.externalSystemId
        } ?: return
        val projectImportProviders = arrayOf(provider)

        if (settingsFile != null) {
            PropertiesComponent.getInstance().setValue(
                "last.imported.location",
                settingsFile.canonicalPath
            )
        }

        ImportModuleAction.doImport(project) {
            ImportModuleAction.selectFileAndCreateWizard(
                project,
                null,
                manager.externalProjectDescriptor,
                projectImportProviders
            )
        }
    }

    private fun tryFindGradleSettings(scriptUnderRoot: ScriptUnderRoot): File? {
        try {
            var parent = File(scriptUnderRoot.filePath).canonicalFile.parentFile
            while (parent.isDirectory) {
                listOf("settings.gradle", "settings.gradle.kts").forEach {
                    val settings = parent.resolve(it)
                    if (settings.isFile) {
                        return settings
                    }
                }

                parent = parent.parentFile
            }
        } catch (_: Throwable) {
            // ignore
        }

        return null
    }

    private fun EditorNotificationPanel.contextHelp(@Nls text: String) {
        val helpIcon = createActionLabel("") {}
        helpIcon.icon = AllIcons.General.ContextHelp
        helpIcon.setUseIconAsLink(true)
        helpIcon.toolTipText = text
    }
}