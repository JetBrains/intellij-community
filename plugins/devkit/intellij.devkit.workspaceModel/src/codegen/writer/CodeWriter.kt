// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.codegen.writer

import com.intellij.devkit.workspaceModel.CodegenJarLoader
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.devkit.workspaceModel.metaModel.impl.WorkspaceMetaModelProviderImpl
import com.intellij.lang.ASTNode
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FactoryMap
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjModule
import com.intellij.workspaceModel.codegen.engine.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.resolve.ImportPath
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.Manifest

private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresEdt
  suspend fun generate(
    project: Project, module: Module, sourceFolder: VirtualFile,
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean, isTestModule: Boolean,
    targetFolderGenerator: () -> VirtualFile?
  ) {
    val ktClasses = HashMap<String, KtClass>()
    VfsUtilCore.processFilesRecursively(sourceFolder) {
      if (it.extension == "kt") {
        val ktFile = PsiManager.getInstance(project).findFile(it) as? KtFile?
        ktFile?.declarations?.filterIsInstance<KtClass>()?.filter { clazz -> clazz.name != null }?.associateByTo(ktClasses) { clazz ->
          clazz.fqName!!.asString()
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

    CommandProcessor.getInstance().executeCommand(project, Runnable {
      val title = DevKitWorkspaceModelBundle.message("progress.title.generating.code")
      ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(title, project, null) { indicator ->
        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.collecting.classes.metadata")
        val objModules = loadObjModules(ktClasses, module, processAbstractTypes)

        val results = generate(codeGenerator, objModules, explicitApiEnabled, isTestModule)
        val generatedCode = results.flatMap { it.generatedCode }
        val problems = results.flatMap { it.problems }
        WorkspaceCodegenProblemsProvider.getInstance(project).reportProblems(problems)

        if (generatedCode.isEmpty() || problems.any { it.level == GenerationProblem.Level.ERROR }) {
          LOG.info("Not found types for generation")
          return@runWriteActionWithCancellableProgressInDispatchThread
        }

        val genFolder = targetFolderGenerator.invoke()
        if (genFolder == null) {
          LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
          return@runWriteActionWithCancellableProgressInDispatchThread
        }

        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.removing.old.code")
        val psiFactory = KtPsiFactory(project)

        removeGeneratedCode(ktClasses, genFolder)

        val topLevelDeclarations = MultiMap.create<KtFile, Pair<KtClass, List<KtDeclaration>>>()
        val importsByFile = FactoryMap.create<KtFile, Imports> { Imports(it.packageFqName.asString()) }
        val generatedFiles = ArrayList<KtFile>()
        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.writing.code")
        indicator.isIndeterminate = false
        generatedCode.forEachIndexed { i, code ->
          indicator.fraction = 0.15 + 0.1 * i / generatedCode.size
          when (code) {
            is ObjModuleFileGeneratedCode ->
              addGeneratedObjModuleFile(code, generatedFiles, project, sourceFolder, genFolder, importsByFile, psiFactory)
            is ObjClassGeneratedCode ->
              addGeneratedObjClassFile(code, generatedFiles, project, sourceFolder, genFolder, ktClasses,
                                       importsByFile, topLevelDeclarations, psiFactory)
          }
        }

        indicator.text = DevKitWorkspaceModelBundle.message("progress.text.formatting.generated.code")
        importsByFile.forEach { (file, imports) ->
          addImports(file, imports.set)
        }
        generatedFiles.withoutBigFiles().forEachIndexed { i, file ->
          indicator.fraction = 0.25 + 0.7 * i / generatedFiles.size
          CodeStyleManager.getInstance(project).reformat(file)
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
    }, DevKitWorkspaceModelBundle.message("command.name.generate.code.for.workspace.entities.in", sourceFolder.name), null)
  }

  private fun codegenApiVersionsAreCompatible(project: Project, codeGeneratorFromDownloadedJar: CodeGenerator): Boolean {
    val apiVersionInDevkit = getApiVersionFromManifest(CodeGenerator::class.java)
    val apiVersionFromDownloadedJar = getApiVersionFromManifest(codeGeneratorFromDownloadedJar::class.java)

    if (apiVersionInDevkit == apiVersionFromDownloadedJar) {
      return true
    }

    val message = if (apiVersionFromDownloadedJar == UNKNOWN_CODEGEN_API_VERSION || apiVersionInDevkit > apiVersionFromDownloadedJar) {
      DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.newer", apiVersionInDevkit, apiVersionFromDownloadedJar)
    } else {
      DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.older", apiVersionInDevkit, apiVersionFromDownloadedJar)
    }

    val groupId = DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions")
    NotificationGroupManager.getInstance()
      .getNotificationGroup(groupId)
      .createNotification(title = groupId, message, NotificationType.ERROR)
      .notify(project)

    return false
  }

  private fun getApiVersionFromManifest(clazz: Class<*>): String {
    val classPath = "${clazz.name.replace(".", "/")}.class"
    val classAbsolutePath = clazz.getResource("${clazz.simpleName}.class")?.toString() // Absolute path is jar path + class path
                            ?: error("Absolute path for the class $clazz was not found")

    val manifestPath = classAbsolutePath.replace(classPath, "META-INF/MANIFEST.MF")

    val apiVersion: String?
    URL(manifestPath).openStream().use {
      val manifest = Manifest(it)
      val attributes = manifest.mainAttributes
      apiVersion = attributes.getValue(CODEGEN_API_VERSION_MANIFEST_ATTRIBUTE)
    }

    return apiVersion ?: UNKNOWN_CODEGEN_API_VERSION
  }

  private fun loadObjModules(ktClasses: HashMap<String, KtClass>, module: Module, processAbstractTypes: Boolean): List<CompiledObjModule> {
    val packages = ktClasses.values.mapTo(LinkedHashSet()) { it.containingKtFile.packageFqName.asString() }

    val metaModelProvider: WorkspaceMetaModelProvider = WorkspaceMetaModelProviderImpl(
      processAbstractTypes = processAbstractTypes,
      module.project
    )
    return packages.map { metaModelProvider.getObjModule(it, module) }
  }

  private fun generate(codeGenerator: CodeGenerator, objModules: List<CompiledObjModule>,
                       explicitApiEnabled: Boolean, isTestModule: Boolean): List<GenerationResult> {
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

  private fun addGeneratedObjModuleFile(code: ObjModuleFileGeneratedCode, generatedFiles: MutableList<KtFile>,
                                        project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
                                        importsByFile: MutableMap<KtFile, Imports>, psiFactory: KtPsiFactory) {
    val packageFqnName = code.objModuleName
    val packageFolder = createPackageFolderIfMissing(sourceFolder, packageNameToPath(packageFqnName), genFolder)
    val targetDirectory = PsiManager.getInstance(project).findDirectory(packageFolder)!!

    val implImports = Imports(packageFqnName)
    val implFile = psiFactory.createFile("${code.fileName}.kt", implImports.findAndRemoveFqns(code.generatedCode))

    targetDirectory.findFile(implFile.name)?.delete()

    val addedFile = targetDirectory.add(implFile) as KtFile
    generatedFiles.add(addedFile)
    importsByFile[addedFile] = implImports
  }

  private fun addGeneratedObjClassFile(code: ObjClassGeneratedCode, generatedFiles: MutableList<KtFile>,
                              project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
                              ktClasses: Map<String, KtClass>, importsByFile: MutableMap<KtFile, Imports>,
                              topLevelDeclarations: MultiMap<KtFile, Pair<KtClass, List<KtDeclaration>>>, psiFactory: KtPsiFactory) {

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
      val relativePath = VfsUtil.getRelativePath(sourceFile.parent, sourceFolder, '/')
      val packageFolder = VfsUtil.createDirectoryIfMissing(genFolder, "$relativePath")
      val targetDirectory = PsiManager.getInstance(project).findDirectory(packageFolder)!!
      val implImports = Imports(apiFile.packageFqName.asString())
      val implFile = psiFactory.createFile("${code.target.name}Impl.kt", implImports.findAndRemoveFqns(implementationClassText))
      copyHeaderComment(apiFile, implFile)
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

  private fun addImports(file: KtFile, imports: Collection<String>) {
    val psiFactory = KtPsiFactory(file.project)
    val importList = file.importList ?: error("no imports in ${file.name}")
    val existingImports = importList.imports.mapNotNullTo(HashSet()) { it.importedFqName?.asString() }
    imports.sorted().filterNot { it in existingImports }.forEach { imported ->
      val place = importList.imports.find { imported < (it.importedFqName?.asString() ?: "") }
      val importDirective = psiFactory.createImportDirective(ImportPath.fromString(imported))
      if (place != null) {
        val added = importList.addBefore(importDirective, place)
        importList.addAfter(psiFactory.createNewLine(), added)
      }
      else {
        val added = importList.add(importDirective)
        importList.addBefore(psiFactory.createNewLine(), added)
      }
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
      else if (regionStart != null && child.isGeneratedRegionEnd) {
        generatedRegions.add(regionStart!! to child)
      }
    }
    return generatedRegions
  }

  private fun createPackageFolderIfMissing(sourceRoot: VirtualFile, packagePath: String, genFolder: VirtualFile): VirtualFile {
    val relativePath = getRelativePathWithoutCommonPrefix(sourceRoot.path, packagePath)
    return VfsUtil.createDirectoryIfMissing(genFolder, relativePath)
  }


  private const val CODEGEN_API_VERSION_MANIFEST_ATTRIBUTE = "Codegen-Api-Version"

  private const val UNKNOWN_CODEGEN_API_VERSION = "unknown version"

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


  private fun getRelativePathWithoutCommonPrefix(base: String, relative: String): String {
    val basePath = Paths.get(FileUtil.normalize(base))
    val relativePath = Paths.get(FileUtil.normalize(relative))

    for (i in 0 until basePath.nameCount) {
      val basePathSuffix = basePath.subpathOrNull(i, basePath.nameCount)
      if (basePathSuffix != null && relativePath.startsWith(basePathSuffix)) {
        return relativePath.subpathOrNull(basePathSuffix.nameCount, relativePath.nameCount)?.toString() ?: ""
      }
    }

    return relative
  }

  private fun Path.subpathOrNull(beginIndex: Int, endIndex: Int): Path? {
    return if (beginIndex < endIndex) subpath(beginIndex, endIndex).addSeparator() else null
  }

  private fun Path.addSeparator(): Path = Paths.get("/${toString()}")

  private fun packageNameToPath(packageName: String): String = "/${packageName.replace('.', '/')}"

  //Function was added because CodeStyleManager throws an exception for big MetadataStorageImpl files
  private fun Iterable<KtFile>.withoutBigFiles(): Iterable<KtFile> {
    return filterNot { it.name == GENERATED_METADATA_STORAGE_FILE }
  }

  private val ObjModule.isTestEntitiesPackage: Boolean
    get() = name == TestEntities.CACHE_VERSION_PACKAGE || name == TestEntities.CURRENT_VERSION_PACKAGE
}

private object TestEntities {
  private const val TEST_ENTITIES_PACKAGE = "com.intellij.platform.workspace.storage.testEntities.entities"

  const val CACHE_VERSION_PACKAGE = "$TEST_ENTITIES_PACKAGE.cacheVersion"
  const val CURRENT_VERSION_PACKAGE = "$TEST_ENTITIES_PACKAGE.currentVersion"
}
