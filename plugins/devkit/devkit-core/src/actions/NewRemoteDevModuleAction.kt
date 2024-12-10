// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.onceWhenFocusGained
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDirectory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.*
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import javax.swing.JComponent

private val LOG = fileLogger()

private class NewRemoteDevModuleAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selectedDirectory = getSelectedDirectory(e)!!

    val project = selectedDirectory.project

    val dialogResult = showNewRemoteDevModuleDialog(project) ?: return

    project.service<NewRemoteDevModuleCoroutineScopeProvider>().cs.launch(Dispatchers.IO) {
      try {
        withBackgroundProgress(project, "Creating Remote Dev Module...") {
          createNewRemoteDevModule(project, selectedDirectory, dialogResult)
        }
      }
      catch (e: Exception) {
        LOG.warn("Couldn't create Remote Dev Module", e)
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(project, "Remote Dev Module created only partially. An exception occured during the process: ${e.message}.", "Remote Dev Module Creation Failed")
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val dataContext = e.dataContext
    if (e.uiKind == ActionUiKind.MAIN_MENU || e.uiKind == ActionUiKind.SEARCH_POPUP) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(dataContext.getData(CommonDataKeys.PROJECT))) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (getSelectedDirectory(e) == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
  }

  private fun getSelectedDirectory(e: AnActionEvent): PsiDirectory? {
    val dataContext = e.dataContext
    val view = dataContext.getData(LangDataKeys.IDE_VIEW)
    return view?.getOrChooseDirectory()
  }

  private suspend fun createNewRemoteDevModule(project: Project, rootDirectory: PsiDirectory, dialogResult: NewRemoteDevModuleDialogResult) {
    val moduleName = dialogResult.moduleName.replace('-', '.').replace('_', '.')

    val moduleType = if (moduleName.endsWith("backend")) {
      ModuleType.BACKEND
    }
    else {
      ModuleType.FRONTEND
    }

    // create module's directories structure
    val moduleDirectories = writeAction {
      val newModuleDirectory = rootDirectory.createSubdirectory(moduleName)
      val newSourcesDirectory = newModuleDirectory.createSubdirectory("src")
      val newResourcesDirectory = newModuleDirectory.createSubdirectory("resources")

      // create package directories
      var newPackageFolderRoot = newSourcesDirectory
      for (packageDirName in getPackageName(moduleName).split('.')) {
        newPackageFolderRoot = newPackageFolderRoot.createSubdirectory(packageDirName)
      }

      object {
        val moduleRoot = newModuleDirectory
        val sourcesDirectory = newSourcesDirectory
        val resourcesDirectory = newResourcesDirectory
      }
    }

    // create module structure
    val module = writeAction {
      val moduleManager = ModuleManager.getInstance(project)
      val modifiableModel = moduleManager.getModifiableModel()
      // TODO: handle exception on `toNioPath`
      val module = modifiableModel.newModule(moduleDirectories.moduleRoot.virtualFile.toNioPath().resolve(moduleName), StdModuleTypes.JAVA.id)
      modifiableModel.commit()

      ModuleRootModificationUtil.updateModel(module) {
        it.addContentEntry(moduleDirectories.moduleRoot.virtualFile)
        it.inheritSdk()
        it.contentEntries.single().addSourceFolder(moduleDirectories.resourcesDirectory.virtualFile, JavaResourceRootType.RESOURCE)
        it.contentEntries.single().addSourceFolder(moduleDirectories.sourcesDirectory.virtualFile, false)
      }

      // add kotlin stdlib
      val kotlinStdLib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName("kotlin-stdlib")

      if (kotlinStdLib != null) {
        ModuleRootModificationUtil.updateModel(module) {
          it.addLibraryEntry(kotlinStdLib)
        }
      }
      else {
        LOG.info("kotlin-stdlib library is not found in project libraries")
      }

      // add platform.kernel dependency
      val kernelModule = moduleManager.findModuleByName("intellij.platform.kernel")
      if (kernelModule != null) {
        ModuleRootModificationUtil.updateModel(module) {
          it.addModuleOrderEntry(kernelModule)
        }
      }
      else {
        LOG.info("intellij.platform.kernel module is not found in project modules")
      }

      module
    }

    // without saving `*.iml` file won't appear in Project View
    saveFiles(project)

    createPluginXml(project, module, moduleDirectories.resourcesDirectory, moduleType)

    // add FacetManagers with Kotlin Compiler plugins
    addKotlinPlugins(project, moduleDirectories.moduleRoot, module)
  }

  private fun showNewRemoteDevModuleDialog(project: Project): NewRemoteDevModuleDialogResult? {
    val dialog = NewRemoteDevModuleDialog(project)
    if (!dialog.showAndGet()) {
      return null
    }
    return NewRemoteDevModuleDialogResult(dialog.newModuleName!!)
  }

  @Suppress("PluginXmlValidity")
  private fun getPluginXmlInitialContent(moduleName: String, moduleType: ModuleType): String {
    val dependency = when (moduleType) {
      ModuleType.FRONTEND -> "<plugin id=\"com.intellij.platform.experimental.frontend\"/>"
      ModuleType.BACKEND -> "<module name=\"intellij.platform.kernel.backend\"/>"
    }
    //language=XML
    return """
      <idea-plugin package="${getPackageName(moduleName)}">
        <dependencies>
         $dependency
        </dependencies>
      </idea-plugin>
    """.trimIndent()
  }

  private suspend fun createPluginXml(project: Project, module: Module, resourcesDir: PsiDirectory, moduleType: ModuleType) {
    val content = getPluginXmlInitialContent(module.name, moduleType)

    writeAction {
      val pluginXmlFile = resourcesDir.createFile("${module.name}.xml")
      pluginXmlFile.viewProvider.virtualFile.writeText(content)
    }

    saveFiles(project)
  }

  private fun getPackageName(moduleName: String): String {
    return "com.$moduleName"
  }

  private suspend fun saveFiles(project: Project) {
    writeAction {
      SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(project = project, forceSavingAllSettings = true), forceExecuteImmediately = true)
    }
  }

  private suspend fun addKotlinPlugins(project: Project, moduleDirectory: PsiDirectory, module: Module) {
    // For now the kotlin plugins are copied from `intellij.platform.debugger.impl.frontend` module,
    // hopefully it won't be moved to a new location
    val debuggerFrontendImlContent = readAction {
      val debuggerFrontendModule = ModuleManager.getInstance(project).findModuleByName("intellij.platform.debugger.impl.frontend") ?: run {
        LOG.info("intellij.platform.debugger.impl.frontend module is not found in project modules")
        return@readAction null
      }
      val debuggerFrontendIml = debuggerFrontendModule.moduleFile
      debuggerFrontendIml?.readText()
    } ?: run {
      LOG.info("intellij.platform.debugger.impl.frontend module file is not found in project modules")
      return
    }

    // Let's copy "FacetManager" part from the debugger to our newely created module
    val debuggerJDOM = JDOMUtil.load(debuggerFrontendImlContent)
    val debuggerFacetManagerComponent = JDomSerializationUtil.findComponent(debuggerJDOM, "FacetManager") ?: run {
      LOG.info("FacetManager component is not found in intellij.platform.debugger.impl.frontend module")
      return
    }

    // Tricky part -- we are waiting here for the creation of the module.virtualFile, it happens asynchronously
    // also we are waiting for it to be deserializable.
    val moduleIml = withTimeoutOrNull(10_000) {
      withContext(Dispatchers.IO) {
        var moduleIml: VirtualFile? = null
        while (isActive && (moduleIml == null || runCatching { JDOMUtil.load(moduleIml.readText()) }.isFailure)) {
          moduleIml = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(moduleDirectory.virtualFile.toNioPath().resolve("${module.name}.iml"))
          delay(200)
        }
        moduleIml
      }
    } ?: run {
      LOG.info("module file is not found for module ${module.name}")
      return
    }

    val moduleImlContent = readAction {
      moduleIml.readText()
    }

    val moduleJDOM = JDOMUtil.load(moduleImlContent)
    val moduleFacetManager = JDomSerializationUtil.findOrCreateComponentElement(moduleJDOM, "FacetManager")
    for (content in debuggerFacetManagerComponent.content) {
      moduleFacetManager.addContent(content.clone())
    }

    writeAction {
      moduleIml.getOutputStream(this@NewRemoteDevModuleAction).use {
        JDOMUtil.write(moduleJDOM, it)
      }
    }

    saveFiles(project)
  }
}


private data class NewRemoteDevModuleDialogResult(val moduleName: String)

private class NewRemoteDevModuleDialog(project: Project) : DialogWrapper(project) {
  private lateinit var moduleNameTextField: JBTextField

  var newModuleName: String? = null

  init {
    title = "New Remote Dev Module"
    init()
  }

  override fun createCenterPanel(): JComponent? = panel {
    row("Remote Dev Module Name:") {
      textField()
        .align(AlignX.FILL)
        .focused()
        .text("intellij.platform.")
        .columns(COLUMNS_LARGE)
        .applyToComponent {
          moduleNameTextField = this
          onceWhenFocusGained {
            select(text.length, text.length)
          }
        }
        .validationOnApply {
          val text = it.text.trim()
          if (!text.startsWith("intellij.platform")) {
            return@validationOnApply error("Module name should start with `intellij.platform`, since action supports creation of platform frontend and backend modules only")
          }
          if (!(text.endsWith(".backend") || text.endsWith(".frontend"))) {
            return@validationOnApply error("Module name should end with `.backend` or `.frontend`, since action supports creation of platform frontend and backend modules only")
          }
          null
        }
    }
    row {
      comment("This action is Experimental! <br> Use the latest nightly build when using the action, otherwise there may be incomapibility issues. <br> See <a href=\"https://youtrack.jetbrains.com/articles/IJPL-A-931/Remote-Dev-Modules-Split\">IJPL-A-931</a> for more details.")
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    newModuleName = moduleNameTextField.text.trim()
  }
}

private enum class ModuleType {
  FRONTEND, BACKEND
}

@Service(Service.Level.PROJECT)
private class NewRemoteDevModuleCoroutineScopeProvider(private val project: Project, val cs: CoroutineScope)