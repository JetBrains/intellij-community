// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.util.onceWhenFocusGained
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.xml.DomManager
import kotlinx.coroutines.*
import org.jetbrains.idea.devkit.actions.ModuleType.*
import org.jetbrains.idea.devkit.dom.IdeaPlugin
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
        val result = withBackgroundProgress(project, "Creating Remote Dev Module...") {
          createNewRemoteDevModule(project, selectedDirectory, dialogResult)
        }
        withContext(Dispatchers.EDT) {
          showResultDialog(project, result)
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
    if (!Registry.`is`("devkit.new.remdev.module.action")) {
      e.presentation.isEnabledAndVisible = false
      return
    }
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

  private fun showNewRemoteDevModuleDialog(project: Project): NewRemoteDevModuleDialogResult? {
    val dialog = NewRemoteDevModuleDialog(project)
    if (!dialog.showAndGet()) {
      return null
    }
    return NewRemoteDevModuleDialogResult(dialog.newModuleName!!)
  }

  private suspend fun createNewRemoteDevModule(project: Project, rootDirectory: PsiDirectory, dialogResult: NewRemoteDevModuleDialogResult): CreateRemoteDevModuleResult {
    val steps = mutableListOf<CreateRemoteDevModuleStep>()
    val moduleName = dialogResult.moduleName.replace('-', '.').replace('_', '.')

    val isPlatform = moduleName.startsWith("intellij.platform")

    val moduleType = when {
      moduleName.endsWith("backend") -> BACKEND
      moduleName.endsWith("frontend") -> FRONTEND
      else -> SHARED
    }

    val moduleDirectories = createRemoteDevModuleDirectories(rootDirectory, moduleName)
    // create module structure
    val module = createRemoteDevIml(project, moduleDirectories, moduleName)

    steps.add(CreateRemoteDevModuleStep.Success("iml is created"))

    // without saving `*.iml` file won't appear in Project View
    saveFiles(project)

    steps.add(createPluginXml(project, module, moduleDirectories.resourcesDirectory, moduleType))

    if (isPlatform) {
      steps.add(patchRemoteDevImls(project, module, moduleType))
    }
    else {
      steps.add(CreateRemoteDevModuleStep.Failed("Patch imls manually, so the module will be included in run configurations"))
    }

    if (isPlatform) {
      steps.add(patchEssentialModulesXml(project, module))
    }
    else {
      steps.add(CreateRemoteDevModuleStep.Failed("Patch plugin.xml of the plugin manually"))
    }

    // add FacetManagers with Kotlin Compiler plugins
    steps.add(addKotlinPlugins(project, moduleDirectories.moduleRoot, module))

    return CreateRemoteDevModuleResult(steps)
  }

  private suspend fun createRemoteDevIml(
    project: Project,
    moduleDirectories: RemoteDevModuleDirectories,
    moduleName: String,
  ): Module = edtWriteAction {
    val moduleManager = ModuleManager.getInstance(project)
    val modifiableModel = moduleManager.getModifiableModel()
    // TODO: handle exception on `toNioPath`
    val module = modifiableModel.newModule(moduleDirectories.moduleRoot.virtualFile.toNioPath().resolve(moduleName), JavaModuleType.getModuleType().id)
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

  private suspend fun createRemoteDevModuleDirectories(rootDirectory: PsiDirectory, moduleName: String): RemoteDevModuleDirectories {
    return edtWriteAction {
      val newModuleDirectory = rootDirectory.createSubdirectory(moduleName)
      val newSourcesDirectory = newModuleDirectory.createSubdirectory("src")
      val newResourcesDirectory = newModuleDirectory.createSubdirectory("resources")

      // create package directories
      var newPackageFolderRoot = newSourcesDirectory
      for (packageDirName in getPackageName(moduleName).split('.')) {
        newPackageFolderRoot = newPackageFolderRoot.createSubdirectory(packageDirName)
      }

      RemoteDevModuleDirectories(newModuleDirectory, newSourcesDirectory, newResourcesDirectory)
    }
  }

  @Suppress("PluginXmlValidity")
  private fun getPluginXmlInitialContent(moduleName: String, moduleType: ModuleType): String {
    val dependencies = when (moduleType) {
      ModuleType.FRONTEND ->
        //language=XML
        """
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      """.trimIndent()
      ModuleType.BACKEND ->
        //language=XML
        """
        <dependencies>
          <module name="intellij.platform.kernel.backend"/>
        </dependencies>
      """.trimIndent()
      ModuleType.SHARED -> ""
    }
    //language=XML
    return """
      <idea-plugin package="${getPackageName(moduleName)}">
        $dependencies
      </idea-plugin>
    """.trimIndent()
  }

  private suspend fun createPluginXml(project: Project, module: Module, resourcesDir: PsiDirectory, moduleType: ModuleType): CreateRemoteDevModuleStep {
    try {
      val content = getPluginXmlInitialContent(module.name, moduleType)

      edtWriteAction {
        val pluginXmlFile = resourcesDir.createFile("${module.name}.xml")
        pluginXmlFile.viewProvider.virtualFile.writeText(content)
      }

      saveFiles(project)
      return CreateRemoteDevModuleStep.Success("plugin.xml is created")
    }
    catch (e: Exception) {
      LOG.info("Couldn't create plugin.xml", e)
      return CreateRemoteDevModuleStep.Success("Couldn't create plugin.xml")
    }
  }

  private suspend fun patchEssentialModulesXml(project: Project, module: Module): CreateRemoteDevModuleStep {
    val couldntPatchEssentialModulesStep = CreateRemoteDevModuleStep.Failed("Couldn't patch essential-modules.xml")
    try {
      val platformResourcesModule = readAction {
        ModuleManager.getInstance(project).findModuleByName("intellij.platform.resources")
      } ?: run {
        LOG.info("intellij.platform.resources module is not found in project modules")
        return couldntPatchEssentialModulesStep
      }
      val essentialModulesXmlFile = readAction {
        ModuleRootManager.getInstance(platformResourcesModule).getSourceRoots(JavaResourceRootType.RESOURCE).firstNotNullOfOrNull {
          it.findChild("META-INF")?.findChild("essential-modules.xml")
        }
      } ?: run {
        LOG.info("essentialModules.xml file is not found in intellij.platform.resources module")
        return couldntPatchEssentialModulesStep
      }

      val essentialModulesXmlPsi = readAction {
        PsiManager.getInstance(module.getProject()).findFile(essentialModulesXmlFile) as? XmlFile
      } ?: run {
        LOG.info("cannot get PSI from essentialModules.xml")
        return couldntPatchEssentialModulesStep
      }

      edtWriteAction {
        CommandProcessor.getInstance().runUndoTransparentAction {
          val fileElement = DomManager.getDomManager(project).getFileElement(essentialModulesXmlPsi, IdeaPlugin::class.java)
                            ?: return@runUndoTransparentAction
          val moduleEntry = fileElement.rootElement.getFirstOrAddContentDescriptor().addModuleEntry()
          moduleEntry.name.stringValue = module.name
        }
      }

      return CreateRemoteDevModuleStep.Success("module is added to essential-modules.xml")
    }
    catch (e: Exception) {
      LOG.info("Couldn't patch essential-modules.xml", e)
      return couldntPatchEssentialModulesStep
    }
  }

  private suspend fun patchRemoteDevImls(project: Project, module: Module, moduleType: ModuleType): CreateRemoteDevModuleStep {
    val couldntPatchPlatformImls = CreateRemoteDevModuleStep.Failed("Couldn't patch platform imls")
    try {
      val moduleNameToPatch = when (moduleType) {
        FRONTEND -> "intellij.platform.frontend.main"
        BACKEND -> "intellij.platform.backend.main"
        SHARED -> "intellij.platform.main"
      }
      val moduleToPatch = readAction {
        ModuleManager.getInstance(project).findModuleByName(moduleNameToPatch)
      } ?: run {
        LOG.info("$moduleNameToPatch module is not found in project modules")
        return couldntPatchPlatformImls
      }

      edtWriteAction {
        ModuleRootModificationUtil.updateModel(moduleToPatch) {
          it.addModuleOrderEntry(module)
        }
      }
      return CreateRemoteDevModuleStep.Success("Platform imls are patched")
    }
    catch (e: Exception) {
      LOG.info("Couldn't patch platform imls", e)
      return couldntPatchPlatformImls
    }
  }

  private fun getPackageName(moduleName: String): String {
    return "com.$moduleName"
  }

  private suspend fun saveFiles(project: Project) {
    edtWriteAction {
      SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(project = project, forceSavingAllSettings = true), forceExecuteImmediately = true)
    }
  }

  private suspend fun addKotlinPlugins(project: Project, moduleDirectory: PsiDirectory, module: Module): CreateRemoteDevModuleStep {
    // For now the kotlin plugins are copied from `intellij.platform.debugger.impl.frontend` module,
    // hopefully it won't be moved to a new location
    val couldntAddKotlinPluginDependencies = CreateRemoteDevModuleStep.Failed("Couldn't add Kotlin plugin dependencies")
    try {
      val debuggerFrontendImlContent = readAction {
        val debuggerFrontendModule = ModuleManager.getInstance(project).findModuleByName("intellij.platform.debugger.impl.frontend")
                                     ?: run {
                                       LOG.info("intellij.platform.debugger.impl.frontend module is not found in project modules")
                                       return@readAction null
                                     }
        val debuggerFrontendIml = debuggerFrontendModule.moduleFile
        debuggerFrontendIml?.readText()
      } ?: run {
        LOG.info("intellij.platform.debugger.impl.frontend module file is not found in project modules")
        return couldntAddKotlinPluginDependencies
      }

      // Let's copy "FacetManager" part from the debugger to our newely created module
      val debuggerJDOM = JDOMUtil.load(debuggerFrontendImlContent)
      val debuggerFacetManagerComponent = JDomSerializationUtil.findComponent(debuggerJDOM, "FacetManager") ?: run {
        LOG.info("FacetManager component is not found in intellij.platform.debugger.impl.frontend module")
        return couldntAddKotlinPluginDependencies
      }

      // Tricky part -- we are waiting here for the creation of the module.virtualFile, it happens asynchronously
      // also we are waiting for it to be deserializable.
      val moduleIml = withTimeoutOrNull(500) {
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
        return couldntAddKotlinPluginDependencies
      }

      val moduleImlContent = readAction {
        moduleIml.readText()
      }

      val moduleJDOM = JDOMUtil.load(moduleImlContent)
      val moduleFacetManager = JDomSerializationUtil.findOrCreateComponentElement(moduleJDOM, "FacetManager")
      for (content in debuggerFacetManagerComponent.content) {
        moduleFacetManager.addContent(content.clone())
      }

      edtWriteAction {
        moduleIml.getOutputStream(this@NewRemoteDevModuleAction).use {
          JDOMUtil.write(moduleJDOM, it)
        }
      }

      saveFiles(project)
      return CreateRemoteDevModuleStep.Success("Kotlin compiler plugins are added to the module")
    }
    catch (e: Exception) {
      LOG.info("Couldn't add Kotlin plugin dependencies", e)
      return couldntAddKotlinPluginDependencies
    }
  }

  private fun showResultDialog(project: Project, result: CreateRemoteDevModuleResult) {
    val isSomethingFailed = result.steps.any { it is CreateRemoteDevModuleStep.Failed }


    @NlsSafe
    val stepsMessage = "Following steps are completed:\n" + result.steps.joinToString("\n") {
      val icon = when (it) {
        is CreateRemoteDevModuleStep.Failed -> "AllIcons.General.BalloonError"
        is CreateRemoteDevModuleStep.Success -> "AllIcons.Status.Success"
      }
      "<icon src='$icon'> ${it.message}"
    }

    if (isSomethingFailed) {
      val followTheInstructionsMessage = "Please follow the guide <a href=\"https://youtrack.jetbrains.com/articles/IJPL-A-931/Remote-Dev-Modules-Split\">IJPL-A-931</a> and resolve failed steps manually."
      Messages.showErrorDialog(project, stepsMessage + "\n\n" + followTheInstructionsMessage, "Remote Dev Module Created Partially")
    }
    else {
      Messages.showInfoMessage(project, stepsMessage, "Remote Dev Module Created Successfully")
    }
  }

  private sealed class CreateRemoteDevModuleStep(val message: @NlsSafe String) {
    // marked as NlsSafe because it is for internal usage only
    data class Success(val successMessage: @NlsSafe String) : CreateRemoteDevModuleStep(successMessage)

    // marked as NlsSafe because it is for internal usage only
    data class Failed(val error: @NlsSafe String) : CreateRemoteDevModuleStep(error)
  }

  private class CreateRemoteDevModuleResult(val steps: List<CreateRemoteDevModuleStep>)

  private class RemoteDevModuleDirectories(
    val moduleRoot: PsiDirectory,
    val sourcesDirectory: PsiDirectory,
    val resourcesDirectory: PsiDirectory,
  )
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
        .text("intellij.")
        .columns(COLUMNS_LARGE)
        .applyToComponent {
          moduleNameTextField = this
          onceWhenFocusGained {
            select(text.length, text.length)
          }
        }
        .validationOnApply {
          val text = it.text.trim()
          if (!text.startsWith("intellij.")) {
            return@validationOnApply error(
              "Module name should start with `intellij.`, since action supports creation of intellij frontend, backend and shared modules only")
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
  FRONTEND, BACKEND, SHARED
}

@Service(Service.Level.PROJECT)
private class NewRemoteDevModuleCoroutineScopeProvider(private val project: Project, val cs: CoroutineScope)