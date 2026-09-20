// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.WriteAction
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.settings.parsedClassNames
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass

internal val KOTLIN_SCRIPT_ANNOTATION_FQ_NAME: FqName = FqName("kotlin.script.experimental.annotations.KotlinScript")

/**
 * True when [klass] carries `@KotlinScript`.
 *
 * The check runs where analysis is not available, so it reads the PSI only. It follows an import
 * alias, and it does not tell our annotation from another one of the same short name.
 */
internal fun hasKotlinScriptAnnotation(klass: KtClass): Boolean =
    KotlinPsiHeuristics.hasAnnotation(klass, KOTLIN_SCRIPT_ANNOTATION_FQ_NAME)

internal const val SCRIPT_TEMPLATE_MARKER_DIR: String = "META-INF/kotlin/script/templates"

internal fun scriptTemplateMarkerPath(fqName: String): @NlsSafe String = "$SCRIPT_TEMPLATE_MARKER_DIR/${scriptTemplateMarkerFileName(fqName)}"

/** The marker file name of [fqName]. It names a file, so it is never translated. */
internal fun scriptTemplateMarkerFileName(fqName: String): @NlsSafe String = "$fqName.classname"

/** True when a marker file in a project dependency declares [fqName]. */
internal fun isScriptTemplateInDependency(project: Project, fqName: String): Boolean =
    fqName in ScriptTemplatesFromDependenciesCache.getOrDiscover(project).fqns

/** True when the IDE loads the definition, either from the settings or from a marker file on the classpath. */
internal fun isScriptTemplateActive(project: Project, fqName: String): Boolean =
    fqName in KotlinScriptingSettings.getInstance(project).state.parsedClassNames || isScriptTemplateInDependency(project, fqName)

/** The marker file of [fqName] in a resource root of [module], or null when no root holds one. */
internal fun findScriptTemplateMarkerFile(module: Module, fqName: String): VirtualFile? =
    ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE)
        .firstNotNullOfOrNull { it.findFileByRelativePath(scriptTemplateMarkerPath(fqName)) }

/**
 * Creates the marker file of [fqName] in the first resource root of [module], and reports the outcome.
 *
 * A module with no resource root has nowhere to hold the file. Silence there reads as an action that
 * ran and did nothing, so the user gets a notification either way.
 */
internal fun createAndReportScriptTemplateMarkerFile(project: Project, module: Module, fqName: String) {
    val markerFile = createScriptTemplateMarkerFile(module, fqName)
    if (markerFile == null) {
        notifyScriptTemplateMarkerFailed(project, module)
        return
    }
    notifyScriptTemplateMarkerCreated(project, markerFile)
}

/**
 * Creates the marker file of [fqName] in the first resource root of [module].
 *
 * Returns the file, or null when the module holds no resource root. An existing file is returned as it is.
 */
private fun createScriptTemplateMarkerFile(module: Module, fqName: String): VirtualFile? {
    val resourceRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE).firstOrNull() ?: return null
    return WriteAction.compute<VirtualFile?, RuntimeException> {
        val templatesDir = VfsUtil.createDirectoryIfMissing(resourceRoot, SCRIPT_TEMPLATE_MARKER_DIR) ?: return@compute null
        val markerName = scriptTemplateMarkerFileName(fqName)
        templatesDir.findChild(markerName) ?: templatesDir.createChildData(templatesDir, markerName)
    }
}

/** A created marker file changes discovery, so the user gets told and can open it. */
private fun notifyScriptTemplateMarkerCreated(project: Project, markerFile: VirtualFile) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("KotlinScriptNotificationGroup")
        .createNotification(
            KotlinBaseScriptingBundle.message("gutter.script.definition.notification.created"),
            markerFile.name,
            NotificationType.INFORMATION,
        )
        .addAction(
            NotificationAction.createSimpleExpiring(
                KotlinBaseScriptingBundle.message("gutter.script.definition.notification.navigate")
            ) { revealScriptTemplateMarker(project, markerFile) }
        )
        .notify(project)
}

/** The module holds no resource root, so the file has nowhere to go. */
private fun notifyScriptTemplateMarkerFailed(project: Project, module: Module) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("KotlinScriptNotificationGroup")
        .createNotification(
            KotlinBaseScriptingBundle.message("gutter.script.definition.notification.failed"),
            KotlinBaseScriptingBundle.message("gutter.script.definition.notification.failed.reason", module.name),
            NotificationType.WARNING,
        )
        .notify(project)
}

/** A marker file holds no text, so the Project view answers where it lives better than the editor. */
internal fun revealScriptTemplateMarker(project: Project, markerFile: VirtualFile) {
    ProjectView.getInstance(project).select(null, markerFile, true)
}
