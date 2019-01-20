// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.json.psi.JsonElementGenerator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.PluginDescriptorChooser
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import java.util.*
import javax.swing.JComponent

//TODO better undo support
class NewThemeAction: AnAction() {
  private val THEME_JSON_TEMPLATE = "ThemeJson.json"
  private val THEME_PROVIDER_EP_NAME = UIThemeProvider.EP_NAME.name


  @Suppress("UsePropertyAccessSyntax") // IdeView#getOrChooseDirectory is not a getter
  override fun actionPerformed(e: AnActionEvent) {
    val view = e.getData(LangDataKeys.IDE_VIEW) ?: return
    val dir = view.getOrChooseDirectory() ?: return

    val module = e.getRequiredData(LangDataKeys.MODULE)
    val project = module.project
    val dialog = NewThemeDialog(project)
    dialog.show()

    if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
      val file = createThemeJson(dialog.name.text, dialog.isDark.isSelected, project, dir)
      view.selectElement(file)
      FileEditorManager.getInstance(project).openFile(file.virtualFile, true)
      registerTheme(dir, file, module)
    }
  }

  override fun update(e: AnActionEvent) {
    val module = e.getData(LangDataKeys.MODULE)
    e.presentation.isEnabled = module != null && ModuleType.get(module) is PluginModuleType //todo[kb] PsiUtil.isPluginModule stopped working
  }

  private fun createThemeJson(themeName: String, isDark: Boolean, project: Project, dir: PsiDirectory): PsiFile {
    val fileName = getThemeJsonFileName(themeName)
    val template = FileTemplateManager.getInstance(project).getJ2eeTemplate(THEME_JSON_TEMPLATE)
    val props = Properties()
    props.setProperty("NAME", JsonElementGenerator(project).createStringLiteral(themeName).text)
    props.setProperty("IS_DARK", isDark.toString())

    val created = FileTemplateUtil.createFromTemplate(template, fileName, props, dir)
    assert(created is PsiFile)
    return created as PsiFile
  }

  private fun getThemeJsonFileName(themeName: String): String {
    return FileUtil.sanitizeFileName(themeName) + ".theme.json"
  }

  private fun registerTheme(dir: PsiDirectory, file: PsiFile, module: Module) {
    val relativeLocation = getRootRelativeLocation(module, file) ?: return

    val pluginXml = DevkitActionsUtil.choosePluginModuleDescriptor(dir) ?: return
    DescriptorUtil.checkPluginXmlsWritable(module.project, pluginXml)
    val domFileElement = DescriptorUtil.getIdeaPlugin(pluginXml)

    WriteCommandAction.writeCommandAction(module.project, pluginXml).run<Throwable> {
      val extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(domFileElement, THEME_PROVIDER_EP_NAME)
      val extensionTag = extensions.addExtension(THEME_PROVIDER_EP_NAME).xmlTag
      extensionTag.setAttribute("id", getRandomId())
      extensionTag.setAttribute("path", relativeLocation)
    }
  }

  //TODO review, probably this implementation doesn't cover all cases
  private fun getRootRelativeLocation(module: Module, file: PsiFile): String? {
    val rootManager = ModuleRootManager.getInstance(module)
    val sourceRoots = rootManager.getSourceRoots(false)
    val virtualFile = file.virtualFile

    var relativeLocation : String? = null
    for (sourceRoot in sourceRoots) {
      relativeLocation = VfsUtil.getRelativeLocation(virtualFile, sourceRoot) ?: continue
      break
    }

    return "/${relativeLocation}"
  }

  private fun getRandomId() = UUID.randomUUID().toString()


  class NewThemeDialog(project: Project) : DialogWrapper(project) {
    val name = JBTextField()
    val isDark = CheckBox(DevKitBundle.message("new.theme.dialog.is.dark.checkbox.text"), true)

    init {
      title = DevKitBundle.message("new.theme.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent? {
      return panel {
        row(DevKitBundle.message("new.theme.dialog.name.text.field.text")) {
          cell {name(growPolicy = GrowPolicy.MEDIUM_TEXT)}
        }
        row("") {
          cell { isDark() }
        }
      }
    }

    override fun getPreferredFocusedComponent(): JComponent? {
      return name
    }

    override fun doValidate(): ValidationInfo? {
      if (name.text.isBlank()) return ValidationInfo(DevKitBundle.message("new.theme.dialog.name.empty"), name)
      //TODO max name length, maybe some other restrictions?
      return null
    }
  }
}
