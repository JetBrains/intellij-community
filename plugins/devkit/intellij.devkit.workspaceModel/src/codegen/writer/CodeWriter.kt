// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.codegen.writer

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.CodegenJarLoader
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter.addGeneratedObjModuleFile
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.lang.ASTNode
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FactoryMap
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.*
import kotlinx.coroutines.delay
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.jar.Manifest
import kotlin.time.Duration.Companion.seconds


private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresEdt
  suspend fun generate(
    project: Project, module: Module, sourceFolder: VirtualFile,
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestSourceFolder: Boolean, isTestModule: Boolean,
    targetFolderGenerator: () -> VirtualFile?,
    existingTargetFolder: () -> VirtualFile?,
  ) {
    val sourceFilePerObjModule = HashMap<String, VirtualFile>()
    val ktClasses = HashMap<String, KtClass>()
    VfsUtilCore.processFilesRecursively(sourceFolder) {
      if (it.extension == "kt") {
        val ktFile = PsiManager.getInstance(project).findFile(it) as? KtFile?

        ktFile?.declarations
          ?.filterIsInstance<KtClass>()
          ?.filter { it.name != null }
          ?.forEach { ktClass ->
            val fqName = ktClass.fqName!!.asString()
            val objModuleName = fqName
              .replace(ktClass.name!!, "")
              .substringBeforeLast(".")

            /**
             *  We find one virtual file for each module.
             *  This is necessary to find the relative path for the generated GeneratedObjModuleFile.
             *  See [addGeneratedObjModuleFile] method.
             */
            sourceFilePerObjModule[objModuleName] = it
            ktClasses[fqName] = ktClass
          }
      }
      return@processFilesRecursively true
    }
    if (ktClasses.isEmpty()) return

    val classLoader = CodegenJarLoader.getInstance(project).getClassLoader()
    val serviceLoader = ServiceLoader.load(CodeGenerator::class.java, classLoader).findFirst()
    if (serviceLoader.isEmpty) error("Can't load generator")

    val codeGenerator = serviceLoader.get()
    if (!codegenApiVersionsAreCompatible(project, codeGenerator)) {
      return
    }

    waitSmartMode(project)
    executeCommand(project, DevKitWorkspaceModelBundle.message("command.name.generate.code.for.workspace.entities.in", sourceFolder.name)) {
      val title = DevKitWorkspaceModelBundle.message("progress.title.generating.code")
      ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(title, project, null) { indicator ->
        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.collecting.classes.metadata")
        val metaLoader: WorkspaceMetaModelProvider = service<WorkspaceMetaModelProvider>()
        val objModules = metaLoader.loadObjModules(ktClasses, module, processAbstractTypes, isTestSourceFolder)

        val results = generate(codeGenerator, objModules, explicitApiEnabled, isTestModule)
        val generatedCode = results.flatMap { it.generatedCode }
        val problems = results.flatMap { it.problems }
        WorkspaceCodegenProblemsProvider.getInstance(project).reportProblems(problems)

        if (generatedCode.isEmpty() || problems.any { it.level == GenerationProblem.Level.ERROR }) {
          LOG.info("Not found types for generation")
          val genFolder = existingTargetFolder.invoke()
          if (genFolder != null) {
            indicator.text = DevKitWorkspaceModelBundle.message("progress.text.removing.old.code")
            removeGeneratedCode(ktClasses, genFolder)
          }
          return@runWriteActionWithCancellableProgressInDispatchThread
        }

        val genFolder = targetFolderGenerator.invoke()
        if (genFolder == null) {
          LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
          return@runWriteActionWithCancellableProgressInDispatchThread
        }

        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.removing.old.code")
        removeGeneratedCode(ktClasses, genFolder)

        val topLevelDeclarations = MultiMap.create<KtFile, Pair<KtClass, List<KtDeclaration>>>()
        val importsByFile = FactoryMap.create<KtFile, Imports> { Imports(it.packageFqName.asString()) }
        val generatedFiles = ArrayList<KtFile>()

        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.writing.code")
        indicator.isIndeterminate = false

        generatedCode.forEachIndexed { i, code ->
          val psiFactory = KtPsiFactory(project)
          indicator.fraction = 0.15 + 0.1 * i / generatedCode.size
          when (code) {
            is ObjModuleFileGeneratedCode ->
              addGeneratedObjModuleFile(
                code, generatedFiles, project,
                sourceFolder, genFolder,
                sourceFilePerObjModule, importsByFile, psiFactory
              )
            is ObjClassGeneratedCode ->
              addGeneratedObjClassFile(
                code, generatedFiles, project,
                sourceFolder, genFolder,
                ktClasses, importsByFile,
                topLevelDeclarations, psiFactory
              )
          }
        }

        importsByFile.forEach { (file, imports) ->
          addImports(file, imports)
        }

        CodeStyle.runWithLocalSettings(project, CodeStyle.getSettings(project)) { localSettings ->
          localSettings.setUpCodeStyle()
          generatedFiles.withoutBigFiles().forEachIndexed { i, file ->
            indicator.fraction = 0.25 + 0.7 * i / generatedFiles.size
            CodeStyleManager.getInstance(project).reformat(file)
            file.apiFileNameForImplFile?.let { ktClasses[it] }?.containingKtFile?.let { apiFile -> copyHeaderComment(apiFile, file) }
          }
          topLevelDeclarations.entrySet().forEach { (file, placeAndDeclarations) ->
            val addedElements = ArrayList<KtDeclaration>()
            for ((place, declarations) in placeAndDeclarations) {
              var nextPlace: PsiElement = place
              val newElements = ArrayList<KtDeclaration>()
              for (declaration in declarations) {
                val added = file.addAfter(declaration, nextPlace) as KtDeclaration
                newElements.add(added)
                nextPlace = added
              }
              addGeneratedRegionStartComment(file, newElements.first())
              addGeneratedRegionEndComment(file, newElements.last())
              addedElements.addAll(newElements)
            }
          }

          val filesWithGeneratedRegions = ktClasses.values.groupBy { it.containingFile }.toList()
          filesWithGeneratedRegions.forEachIndexed { i, (file, classes) ->
            indicator.fraction = 0.95 + 0.05 * i / filesWithGeneratedRegions.size
            reformatCodeInGeneratedRegions(file, classes.mapNotNull { it.body?.node } + listOf(file.node))
          }

        }
      }
    }
  }

  private fun CodeStyleSettings.setUpCodeStyle() {
    val kotlinCustomSettings = this.kotlinCustomSettings
    kotlinCustomSettings.NAME_COUNT_TO_USE_STAR_IMPORT = Int.MAX_VALUE
  }

  /**
   * Documentation for [com.intellij.openapi.project.IndexNotReadyException] says that it's enough to run completeJustSubmittedTasks only
   * once. However, in practive this is not enough.
   */
  private suspend fun waitSmartMode(project: Project) {
    for (i in 1..100) {
      if (i == 100) error("99 delays were not enough to wait for smart mode")
      if (DumbService.isDumb(project)) {
        DumbService.getInstance(project).completeJustSubmittedTasks()
        delay(1.seconds)
      }
      else break
    }
  }

  private fun codegenApiVersionsAreCompatible(project: Project, codeGeneratorFromDownloadedJar: CodeGenerator): Boolean {
    val apiVersionInDevkit = getApiVersionFromJSON(CodeGenerator::class.java)
    val apiVersionFromDownloadedJar = getApiVersionFromManifest(codeGeneratorFromDownloadedJar::class.java)

    if (apiVersionInDevkit == apiVersionFromDownloadedJar) {
      return true
    }

    val message = if (apiVersionFromDownloadedJar == CodegenApiVersion.UNKNOWN_VERSION || apiVersionInDevkit > apiVersionFromDownloadedJar) {
      DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.newer", apiVersionInDevkit, apiVersionFromDownloadedJar)
    }
    else {
      DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.older", apiVersionInDevkit, apiVersionFromDownloadedJar)
    }

    val groupId = DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions")
    NotificationGroupManager.getInstance()
      .getNotificationGroup(groupId)
      .createNotification(title = groupId, message, NotificationType.ERROR)
      .notify(project)

    return false
  }

  private fun getApiVersionFromJSON(clazz: Class<*>): String {
    return getApiVersionFromJarFile(clazz, CodegenApiVersion.JSON_RELATIVE_PATH) { jsonPath ->
      URL(jsonPath).openStream().reader().use { reader ->
        val jsonReader = JsonReaderEx(reader.readText())
        val objects = JsonUtil.nextObject(jsonReader)
        objects[CodegenApiVersion.ATTRIBUTE_NAME] as? String
      }
    }
  }

  private fun getApiVersionFromManifest(clazz: Class<*>): String {
    return getApiVersionFromJarFile(clazz, CodegenApiVersion.MANIFEST_RELATIVE_PATH) { manifestPath ->
      URL(manifestPath).openStream().use {
        val manifest = Manifest(it)
        val attributes = manifest.mainAttributes
        attributes.getValue(CodegenApiVersion.ATTRIBUTE_NAME)
      }
    }
  }

  private fun getApiVersionFromJarFile(clazz: Class<*>, relativePathToFile: String, readApiVersionFromFile: (String) -> String?): String {
    val classPath = "${clazz.name.replace(".", "/")}.class"
    val classAbsolutePath = clazz.getResource("${clazz.simpleName}.class")?.toString() // Absolute path is jar path + class path
                            ?: error("Absolute path for the class $clazz was not found")

    val fileAbsolutePath = classAbsolutePath.replace(classPath, relativePathToFile)

    val apiVersion: String?
    try {
      apiVersion = readApiVersionFromFile(fileAbsolutePath)
    }
    catch (e: IOException) {
      LOG.info("Failed to read codegen-api version from file \"$fileAbsolutePath\": " + e.message)
      return CodegenApiVersion.UNKNOWN_VERSION
    }

    return apiVersion ?: CodegenApiVersion.UNKNOWN_VERSION
  }

  private fun generate(
    codeGenerator: CodeGenerator, objModules: List<CompiledObjModule>,
    explicitApiEnabled: Boolean, isTestModule: Boolean,
  ): List<GenerationResult> {
    val generatorSettings = GeneratorSettings(explicitApiEnabled = explicitApiEnabled, testModeEnabled = isTestModule)
    val entitiesImplementations = objModules.map { codeGenerator.generateEntitiesImplementation(it, generatorSettings) }
    val metadataStorageImplementation = codeGenerator.generateMetadataStoragesImplementation(objModules, generatorSettings)
    return entitiesImplementations + metadataStorageImplementation
  }

  private fun removeGeneratedCode(ktClasses: Map<String, KtClass>, genFolder: VirtualFile) {
    ktClasses.values.flatMapTo(HashSet()) { listOfNotNull(it.containingFile.node, it.body?.node) }.forEach {
      removeChildrenInGeneratedRegions(it)
    }

    //remove generated files, e.g. MetadataStorageImpl.kt
    removeFiles(genFolder) {
      it.isGeneratedFile
    }

    //remove empty packages
    removeFiles(genFolder) { vfs ->
      vfs.isDirectory && vfs != genFolder && VfsUtil.collectChildrenRecursively(vfs).all { it.isDirectory }
    }
  }

  private fun removeFiles(baseFolder: VirtualFile, filter: (VirtualFile) -> Boolean) {
    val filesToRemove = arrayListOf<VirtualFile>()

    VfsUtilCore.processFilesRecursively(baseFolder) {
      if (filter.invoke(it)) {
        filesToRemove.add(it)
      }
      return@processFilesRecursively true
    }

    filesToRemove.forEach { it.delete(CodeWriter) }
  }

  private fun addGeneratedObjModuleFile(
    code: ObjModuleFileGeneratedCode, generatedFiles: MutableList<KtFile>,
    project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
    sourceFilePerObjModule: Map<String, VirtualFile>,
    importsByFile: MutableMap<KtFile, Imports>, psiFactory: KtPsiFactory,
  ) {
    val packageFqnName = code.objModuleName

    val sourceFile = sourceFilePerObjModule[packageFqnName]!!
    val targetDirectory = getPsiDirectory(project, genFolder, sourceFolder, sourceFile)

    val implPackageFqnName = "$packageFqnName.impl"
    val implImports = Imports(implPackageFqnName)
    val implFile = psiFactory.createFile("${code.fileName}.kt", implImports.findAndRemoveFqns(code.generatedCode))

    targetDirectory.findFile(implFile.name)?.delete()

    val addedFile = targetDirectory.add(implFile) as KtFile
    generatedFiles.add(addedFile)
    importsByFile[addedFile] = implImports
  }

  private fun addGeneratedObjClassFile(
    code: ObjClassGeneratedCode, generatedFiles: MutableList<KtFile>,
    project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
    ktClasses: Map<String, KtClass>, importsByFile: MutableMap<KtFile, Imports>,
    topLevelDeclarations: MultiMap<KtFile, Pair<KtClass, List<KtDeclaration>>>, psiFactory: KtPsiFactory,
  ) {

    if (code.target.name in SKIPPED_TYPES) return

    val target = code.target
    val apiInterfaceName = "${target.module.name}.${target.name}"
    val apiClass = ktClasses[apiInterfaceName] ?: error("Cannot find API class by $apiInterfaceName")
    val apiFile = apiClass.containingKtFile
    val apiImports = importsByFile.getValue(apiFile)
    addInnerDeclarations(apiClass, code, apiImports)
    val topLevelCode = code.topLevelCode
    if (topLevelCode != null) {
      val declarations = psiFactory.createFile(apiImports.findAndRemoveFqns(code.topLevelCode!!)).declarations
      topLevelDeclarations.putValue(apiFile, apiClass to declarations)
    }
    val implementationClassText = code.implementationClass
    if (implementationClassText != null) {
      val sourceFile = apiClass.containingFile.virtualFile
      val targetDirectory = getPsiDirectory(project, genFolder, sourceFolder, sourceFile)

      val implPackageFqnName = "${apiFile.packageFqName.asString()}.impl"
      val implImports = Imports(implPackageFqnName)
      val implFile = psiFactory.createFile("${code.target.name}Impl.kt", implImports.findAndRemoveFqns(implementationClassText))
      apiClass.containingKtFile.importDirectives.mapNotNull { it.importPath }.forEach { import ->
        implImports.add(import.pathStr)
      }
      targetDirectory.findFile(implFile.name)?.delete()
      //todo remove other old generated files
      val addedFile = targetDirectory.add(implFile) as KtFile
      generatedFiles.add(addedFile)
      importsByFile[addedFile] = implImports
    }
  }

  private fun getPsiDirectory(project: Project, genFolder: VirtualFile, sourceFolder: VirtualFile, sourceFile: VirtualFile): PsiDirectory {
    val relativePath = VfsUtil.getRelativePath(sourceFile.parent, sourceFolder, '/')
    // We store entities' implementation in impl package
    val packageFolder = VfsUtil.createDirectoryIfMissing(genFolder, "$relativePath/impl")
    return PsiManager.getInstance(project).findDirectory(packageFolder)!!
  }

  private fun copyHeaderComment(apiFile: KtFile, implFile: KtFile) {
    val apiPackageDirectiveNode = apiFile.packageDirective?.node ?: return
    val fileNode = implFile.node
    var nodeToCopy = apiPackageDirectiveNode.treePrev
    var anchorBefore = fileNode.firstChildNode
    while (nodeToCopy != null) {
      val copied = nodeToCopy.copyElement()
      fileNode.addChild(copied, anchorBefore)
      anchorBefore = copied
      nodeToCopy = nodeToCopy.treePrev
    }
  }

  private fun addImports(file: KtFile, imports: Imports) {
    val psiFactory = KtPsiFactory(file.project)
    val importList = file.importList ?: error("no imports in ${file.name}")
    importList.imports.asSequence().mapNotNull { it.importPath?.pathStr }.forEach { imports.add(it) }
    if (importList.children.isNotEmpty()) {
      importList.deleteChildRange(importList.firstChild, importList.lastChild)
    }
    imports.imports.sorted().forEach {
      val importDirective = psiFactory.createImportDirective(ImportPath.fromString(it))
      importList.add(importDirective)
      importList.add(psiFactory.createNewLine())
    }
    if (imports.isNotEmpty()) {
      importList.lastChild.delete()
    }
  }

  private fun addInnerDeclarations(ktClass: KtClass, code: ObjClassGeneratedCode, imports: Imports) {
    val psiFactory = KtPsiFactory(ktClass.project)
    val builderInterface = ktClass.addDeclaration(psiFactory.createClass(imports.findAndRemoveFqns(code.builderInterface)))
    val companionObject = ktClass.addDeclaration(psiFactory.createObject(imports.findAndRemoveFqns(code.companionObject)))
    val body = ktClass.getOrCreateBody()
    addGeneratedRegionStartComment(body, builderInterface)
    addGeneratedRegionEndComment(body, companionObject)
  }

  private fun addGeneratedRegionStartComment(parent: KtElement, place: KtElement) {
    val psiFactory = KtPsiFactory(parent.project)
    val startCommentElements = psiFactory.createFile("\n$GENERATED_REGION_START\n").children
    parent.addRangeBefore(startCommentElements.first { it is PsiWhiteSpace }, startCommentElements.last(), place)
  }

  private fun addGeneratedRegionEndComment(parent: KtElement, place: KtElement) {
    val psiFactory = KtPsiFactory(parent.project)
    val endCommentElements = psiFactory.createFile("\n$GENERATED_REGION_END\n").children
    parent.addRangeAfter(endCommentElements.first { it is PsiWhiteSpace }, endCommentElements.last(), place)
  }

  private fun removeChildrenInGeneratedRegions(node: ASTNode) {
    for (region in findGeneratedRegions(node)) {
      CodeEditUtil.removeChildren(node, region.first, region.second)
    }
  }

  private fun reformatCodeInGeneratedRegions(file: PsiFile, nodes: List<ASTNode>) {
    val generatedRegions = nodes.flatMap { findGeneratedRegions(it) }
    if (generatedRegions.isEmpty()) return
    val regions = generatedRegions.map { TextRange.create(it.first.startOffset, it.second.startOffset + it.second.textLength) }
    CodeStyleManager.getInstance(file.project).reformatText(file, joinAdjacentRegions(regions))
  }

  private fun joinAdjacentRegions(regions: List<TextRange>): List<TextRange> {
    val result = ArrayList<TextRange>()
    regions.sortedBy { it.startOffset }.forEach { next ->
      val last = result.lastOrNull()
      if (last != null && last.endOffset >= next.startOffset) {
        result[result.lastIndex] = last.union(next)
      }
      else {
        result.add(next)
      }
    }
    return result
  }

  private fun findGeneratedRegions(node: ASTNode): ArrayList<Pair<ASTNode, ASTNode>> {
    val generatedRegions = ArrayList<Pair<ASTNode, ASTNode>>()
    var regionStart: ASTNode? = null
    node.children().forEach { child ->
      if (child.isGeneratedRegionStart) {
        regionStart = if (child.treePrev?.elementType == KtTokens.WHITE_SPACE) child.treePrev else child
      }
      else if (child.isGeneratedRegionEnd) {
        regionStart?.let { generatedRegions.add(it to child) }
      }
    }
    return generatedRegions
  }

  private val KtFile.apiFileNameForImplFile: String?
    get() {
      val packageName = packageFqName.asString()
      if (!packageName.endsWith(".impl") || !name.endsWith("Impl.kt")) return null
      return "${packageFqName.asString().dropLast(4)}${name.dropLast(7)}"
    }

  private const val GENERATED_REGION_START = "//region generated code"

  private const val GENERATED_REGION_END = "//endregion"

  private val ASTNode.isGeneratedRegionStart: Boolean
    get() =
      elementType == KtTokens.EOL_COMMENT && text == GENERATED_REGION_START || firstChildNode?.isGeneratedRegionStart == true

  private val ASTNode.isGeneratedRegionEnd: Boolean
    get() =
      elementType == KtTokens.EOL_COMMENT && text == GENERATED_REGION_END


  private const val GENERATED_METADATA_STORAGE_FILE = "MetadataStorageImpl.kt"

  private val GENERATED_FILES = setOf(GENERATED_METADATA_STORAGE_FILE)

  private val VirtualFile.isGeneratedFile: Boolean
    get() = extension == "kt" && GENERATED_FILES.contains(name)


  private object CodegenApiVersion {
    const val JSON_RELATIVE_PATH = "codegen-api-metadata.json"
    const val MANIFEST_RELATIVE_PATH = "META-INF/MANIFEST.MF"

    const val ATTRIBUTE_NAME = "Codegen-Api-Version"

    const val UNKNOWN_VERSION = "unknown version"
  }

  // This function was added because CodeStyleManager throws an exception for big MetadataStorageImpl files
  private fun Iterable<KtFile>.withoutBigFiles(): Iterable<KtFile> {
    return filterNot { it.name == GENERATED_METADATA_STORAGE_FILE }
  }
}
