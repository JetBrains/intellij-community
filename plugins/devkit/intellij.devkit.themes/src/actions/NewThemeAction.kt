// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.actions

import com.intellij.ide.actions.NewFileActionWithCategory
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.PluginDescriptorChooser
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.themes.DevKitThemesBundle
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.*

internal class NewThemeAction : AnAction(), NewFileActionWithCategory {
  private val THEME_JSON_TEMPLATE = "ThemeJson.json"
  private val THEME_PROVIDER_EP_NAME = UIThemeProvider.EP_NAME.name

  override fun getCategory(): String = "Theme"

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val view = e.getData(LangDataKeys.IDE_VIEW) ?: return
    val dir = view.getOrChooseDirectory() ?: return
    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
    val project = module.project
    lateinit var name: Cell<JBTextField>
    lateinit var isDark: Cell<JBCheckBox>
    val panel = panel {
      row(DevKitThemesBundle.message("new.theme.dialog.name.text.field.text")) {
        name = textField()
          .focused()
          .columns(30)
          .addValidationRule(DevKitThemesBundle.message("new.theme.dialog.name.empty")) { name.component.text.isBlank() }
      }
      row("") {
        isDark = checkBox(DevKitThemesBundle.message("new.theme.dialog.is.dark.checkbox.text")).selected(true)
      }
    }

    val dialog = dialog(DevKitThemesBundle.message("new.theme.dialog.title"), project = project, panel = panel)
    dialog.show()
    if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
      val file = createThemeJson(name.component.text, isDark.component.isSelected, project, dir, module)
      view.selectElement(file)
      FileEditorManager.getInstance(project).openFile(file.virtualFile, true)
      registerTheme(dir, file, module)
    }
  }

  override fun update(e: AnActionEvent) {
    val module = e.getData(PlatformCoreDataKeys.MODULE)
    e.presentation.isEnabledAndVisible = module != null
                                         && (PluginModuleType.get(module) is PluginModuleType || PsiUtil.isPluginModule(module))
  }

  private fun createThemeJson(themeName: String,
                              isDark: Boolean,
                              project: Project,
                              dir: PsiDirectory,
                              module: Module): PsiFile {
    val fileName = getThemeJsonFileName(themeName)
    val colorSchemeFilename = getThemeColorSchemeFileName(themeName)
    val template = FileTemplateManager.getInstance(project).getJ2eeTemplate(THEME_JSON_TEMPLATE)
    val editorSchemeProps = Properties()
    editorSchemeProps.setProperty("NAME", themeName)
    editorSchemeProps.setProperty("PARENT_SCHEME", if (isDark)  "Darcula" else "Default")
    val editorSchemeTemplate = FileTemplateManager.getInstance(project).getJ2eeTemplate("ThemeEditorColorScheme.xml")
    val colorScheme = FileTemplateUtil.createFromTemplate(editorSchemeTemplate, colorSchemeFilename, editorSchemeProps, dir)
    val props = Properties()
    props.setProperty("NAME", themeName)
    props.setProperty("IS_DARK", isDark.toString())
    props.setProperty("COLOR_SCHEME_NAME", getSourceRootRelativeLocation(module, colorScheme as PsiFile))

    val created = FileTemplateUtil.createFromTemplate(template, fileName, props, dir)
    assert(created is PsiFile)
    return created as PsiFile
  }

  private fun getThemeJsonFileName(themeName: String): String {
    return FileUtil.sanitizeFileName(themeName) + ".theme.json"
  }

  private fun getThemeColorSchemeFileName(themeName: String): String {
    return FileUtil.sanitizeFileName(themeName) + ".xml"
  }

  private fun registerTheme(dir: PsiDirectory, file: PsiFile, module: Module) {
    val relativeLocation = getSourceRootRelativeLocation(module, file)

    val pluginXml = DevkitActionsUtil.choosePluginModuleDescriptor(dir) ?: return
    DescriptorUtil.checkPluginXmlsWritable(module.project, pluginXml)
    val domFileElement = DescriptorUtil.getIdeaPluginFileElement(pluginXml)

    WriteCommandAction.writeCommandAction(module.project, pluginXml).run<Throwable> {
      val extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(domFileElement, THEME_PROVIDER_EP_NAME)
      val extensionTag = extensions.addExtension(THEME_PROVIDER_EP_NAME).xmlTag
      extensionTag.setAttribute("id", getRandomId())
      extensionTag.setAttribute("path", relativeLocation)
    }
  }

  private fun getSourceRootRelativeLocation(module: Module, file: PsiFile): String {
    val rootManager = ModuleRootManager.getInstance(module)
    val sourceRoots = rootManager.getSourceRoots(false)
    val virtualFile = file.virtualFile

    var relativeLocation : String? = null
    for (sourceRoot in sourceRoots) {
      if (!VfsUtil.isAncestor(sourceRoot,virtualFile,true)) continue
      relativeLocation = VfsUtil.getRelativeLocation(virtualFile, sourceRoot) ?: continue
      break
    }

    return "/${relativeLocation}"
  }

  private fun getRandomId() = UUID.randomUUID().toString()
}