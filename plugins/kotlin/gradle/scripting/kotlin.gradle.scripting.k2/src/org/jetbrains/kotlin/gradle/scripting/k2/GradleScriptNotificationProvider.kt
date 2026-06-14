// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k2

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
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptRootResolver.NotificationKind.DONT_CARE
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptRootResolver.NotificationKind.OUTSIDE_ANYTHING
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptRootResolver.NotificationKind.WAS_NOT_IMPORTED_AFTER_CREATION
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptingBundle
import org.jetbrains.kotlin.gradle.scripting.shared.isGradleKotlinScript
import org.jetbrains.kotlin.gradle.scripting.shared.runPartialGradleImport
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.function.Function
import javax.swing.JComponent

//TODO: KTIJ-30408
internal class GradleScriptNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!isGradleKotlinScript(file) || !file.isKotlinFileType()) {
            return null
        }

        val scriptUnderRoot = GradleScriptRootResolver.findScriptBuildRoot(project, file) ?: return null
        if (KotlinScriptEntityProvider.findKotlinScriptEntity(project, file) != null) return null

        return Function { fileEditor ->
            when (scriptUnderRoot.notificationKind) {
                DONT_CARE -> null
                OUTSIDE_ANYTHING -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.outsideAnything.text"))
                    createActionLabel(KotlinGradleScriptingBundle.message("notification.outsideAnything.linkAction")) {
                        linkProject(project, scriptUnderRoot)
                    }
                }
                WAS_NOT_IMPORTED_AFTER_CREATION -> EditorNotificationPanel(fileEditor, Status.Warning).apply {
                    text(KotlinGradleScriptingBundle.message("notification.wasNotImportedAfterCreation.text"))
                    createActionLabel(KotlinGradleScriptingBundle.message("action.LoadKtGradleConfiguration.text")) {
                        val root = scriptUnderRoot.nearest
                        if (root != null) {
                            runPartialGradleImport(project, root)
                        }
                    }
                    contextHelp(KotlinGradleScriptingBundle.message("notification.wasNotImportedAfterCreation.help"))
                }
            }
        }
    }

    private fun linkProject(
        project: Project,
        scriptUnderRoot: GradleScriptRootResolver.ScriptUnderRoot,
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

    private fun tryFindGradleSettings(scriptUnderRoot: GradleScriptRootResolver.ScriptUnderRoot): File? {
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
