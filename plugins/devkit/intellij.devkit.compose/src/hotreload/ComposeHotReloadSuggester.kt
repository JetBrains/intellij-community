// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.hotreload

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.devkit.compose.hasCompose
import com.intellij.facet.FacetManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
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
private const val REGISTRY_KEY = "devkit.compose.hot.reload.enabled"

internal class ComposeHotReloadSuggester : EditorNotificationProvider, DumbAware {
  private val SUGGESTION_DISMISSED_KEY = Key.create<Boolean>("HOT_RELOAD_SUGGESTION_DISMISSED")

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (Registry.`is`(REGISTRY_KEY)) return null
    if (!hasCompose(project)) return null

    if (isSuggestionDismissed(file)) return null
    if (!isComposeUiFile(project, file)) return null

    return Function { editor -> Banner(project, editor.file) }
  }

  private fun isSuggestionDismissed(file: VirtualFile): Boolean {
    return file.getUserData(SUGGESTION_DISMISSED_KEY) != null
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

  private inner class Banner(val project: Project, val file: VirtualFile) : EditorNotificationPanel(Status.Success) {
    init {
      @Suppress("DialogTitleCapitalization")
      text = DevkitComposeBundle.message("label.compose.hot.reload.available.banner")
      icon(AllIcons.Debugger.DebuggerSync)

      @Suppress("DialogTitleCapitalization")
      createActionLabel(DevkitComposeBundle.message("link.label.enable.hot.reload")) {
        RegistryManager.getInstance().get(REGISTRY_KEY).setValue(true)

        EditorNotifications.getInstance(project).updateAllNotifications()
      }

      createActionLabel(DevkitComposeBundle.message("link.label.dismiss")) {
        file.putCopyableUserData(SUGGESTION_DISMISSED_KEY, true)

        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }
  }
}