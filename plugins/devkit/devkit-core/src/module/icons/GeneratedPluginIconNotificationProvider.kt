// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module.icons

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.function.Function
import javax.swing.JComponent

/**
 * Notification provider for generated plugin icon files.
 * Shows a banner with a link to regenerate the icon with a new random seed.
 */
internal class GeneratedPluginIconNotificationProvider : EditorNotificationProvider {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    // Check file name
    val fileName = file.name
    if (fileName != "pluginIcon.svg" && fileName != "pluginIcon_dark.svg") {
      return null
    }

    // Check that file is in a plugin module
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val module = ModuleUtilCore.findModuleForPsiElement(psiFile) ?: return null
    if (!PsiUtil.isPluginModule(module)) {
      return null
    }

    // Check for generated marker comment
    if (psiFile !is XmlFile) return null
    if (!psiFile.text.contains(GENERATED_MARKER)) return null

    return Function { _ -> GeneratedIconPanel(project, file) }
  }

  private class GeneratedIconPanel(
    private val project: Project,
    private val file: VirtualFile
  ) : EditorNotificationPanel(Status.Info) {
    init {
      text = DevKitBundle.message("notification.generated.plugin.icon.text")

      createActionLabel(DevKitBundle.message("notification.generated.plugin.icon.regenerate")) {
        regenerateIcons()
      }
    }

    private fun regenerateIcons() {
      val seed = System.currentTimeMillis()
      val lightIcon = IconGenerator.generate(seed, isDark = false)
      val darkIcon = IconGenerator.generate(seed, isDark = true)

      val directory = file.parent ?: return
      val lightFile = directory.findChild("pluginIcon.svg")
      val darkFile = directory.findChild("pluginIcon_dark.svg")

      WriteCommandAction.writeCommandAction(project)
        .withName(DevKitBundle.message("command.name.regenerate.plugin.icons"))
        .run<RuntimeException> {
          lightFile?.let { writeIconToFile(it, lightIcon) }
          darkFile?.let { writeIconToFile(it, darkIcon) }
        }
    }

    private fun writeIconToFile(file: VirtualFile, iconData: IconData) {
      val svgContent = SvgRenderer.render(iconData)
      VfsUtil.saveText(file, svgContent)
    }
  }
}
