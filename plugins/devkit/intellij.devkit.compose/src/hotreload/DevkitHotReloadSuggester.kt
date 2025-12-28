// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.hotreload

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.devkit.compose.hasCompose
import com.intellij.devkit.compose.icons.DevkitComposeIcons
import com.intellij.facet.FacetManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import java.util.function.Function
import javax.swing.JComponent

private const val COMPOSE_HOT_RELOAD_ENABLED_MARKER = "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations"

internal class DevkitHotReloadSuggester : EditorNotificationProvider, DumbAware {
  private val SUGGESTION_DISMISSED_KEY = "COMPOSE_HOT_RELOAD_GOT_IT"

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!isIntelliJPlatformProject(project)) return null
    if (!hasCompose(project)) return null

    if (isSuggestionDismissed(project)) return null
    if (!isComposeUiFile(project, file)) return null

    return Function { _ -> Banner(project) }
  }

  private fun isSuggestionDismissed(project: Project): Boolean {
    return PropertiesComponent.getInstance(project).getBoolean(SUGGESTION_DISMISSED_KEY)
  }

  private fun dismissSuggestion(project: Project) {
    PropertiesComponent.getInstance(project).setValue(SUGGESTION_DISMISSED_KEY, true)
  }

  private fun isComposeUiFile(project: Project, file: VirtualFile): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile !is KtFile) return false

    // check if Kotlin facet enables `-P plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true`
    val module = ModuleUtilCore.findModuleForPsiElement(psiFile) ?: return false
    val facet = FacetManager.getInstance(module).getFacetByType(KotlinFacetType.TYPE_ID) ?: return false
    val args = facet.configuration.settings.compilerSettings?.additionalArguments ?: ""
    if (!args.contains(COMPOSE_HOT_RELOAD_ENABLED_MARKER)) return false

    // only in relevant files with Compose imports
    return psiFile.importDirectives
      .any { ix -> isComposeRuntimeImport(ix) }
  }

  private fun isComposeRuntimeImport(ix: KtImportDirective): Boolean {
    val segments = ix.importedFqName?.pathSegments() ?: return false
    if (segments.size < 3) return false

    return segments[0].identifier == "androidx"
           && segments[1].identifier == "compose"
           && segments[2].identifier == "runtime"
  }

  private inner class Banner(val project: Project) : EditorNotificationPanel(Status.Success) {
    init {
      text = DevkitComposeBundle.message("label.compose.hot.reload.available.banner")
      icon(DevkitComposeIcons.ComposeHotReload)

      createActionLabel(DevkitComposeBundle.message("link.label.dismiss")) {
        dismissSuggestion(project)

        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }
  }
}