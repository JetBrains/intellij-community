// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.codegen.writer

import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.FactoryMap
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.codegen.SKIPPED_TYPES
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.GeneratedCode
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.impl.CodeGeneratorImpl
import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.utils.Imports
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.resolve.ImportPath

private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresWriteLock
  fun generate(project: Project,
               module: Module,
               sourceFolder: VirtualFile,
               targetFolderGenerator: () -> VirtualFile?) {
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

    val objModules = loadObjModules(ktClasses, module)
    
    val codeGenerator = CodeGeneratorImpl()
    val results = objModules.map { codeGenerator.generate(it) }
    val generatedCode = results.flatMap { it.generatedCode }
    val problems = results.flatMap { it.problems }
    WorkspaceCodegenProblemsProvider.getInstance(project).reportProblems(problems)
    
    if (generatedCode.isNotEmpty() && problems.none { it.level == GenerationProblem.Level.ERROR }) {
      val genFolder = targetFolderGenerator.invoke()
      if (genFolder == null) {
        LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
        return
      }

      CommandProcessor.getInstance().executeCommand(project, Runnable {
        val title = DevKitWorkspaceModelBundle.message("progress.title.generating.code")
        ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(title, project, null) { indicator ->
          indicator.text = DevKitWorkspaceModelBundle.message("progress.text.removing.old.code")
          val psiFactory = KtPsiFactory(project)
          ktClasses.values.flatMapTo(HashSet()) { listOfNotNull(it.containingFile.node, it.body?.node) }.forEach {
            removeChildrenInGeneratedRegions(it)
          }
          val topLevelDeclarations = MultiMap.create<KtFile, Pair<KtClass, List<KtDeclaration>>>()
          val importsByFile = FactoryMap.create<KtFile, Imports> { Imports(it.packageFqName.asString()) }
          val generatedFiles = ArrayList<KtFile>()
          indicator.text = DevKitWorkspaceModelBundle.message("progress.text.writing.code")
          indicator.isIndeterminate = false
          generatedCode.forEachIndexed { i, code ->
            indicator.fraction = 0.2 * i / generatedCode.size
            if (code.target.name in SKIPPED_TYPES) return@forEachIndexed

            val apiInterfaceName = code.target.javaFullName.decoded
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
              val packageFolder = createPackageFolderIfMissing(sourceFolder, sourceFile, genFolder)
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

          indicator.text = DevKitWorkspaceModelBundle.message("progress.text.formatting.generated.code")
          importsByFile.forEach { (file, imports) ->
            addImports(file, imports.set)
          }
          generatedFiles.forEachIndexed { i, file ->
            indicator.fraction = 0.2 + 0.7 * i / generatedFiles.size
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
            indicator.fraction = 0.9 + 0.1 * i / filesWithGeneratedRegions.size
            reformatCodeInGeneratedRegions(file, classes.mapNotNull { it.body?.node } + listOf(file.node))
          }

        }
      }, DevKitWorkspaceModelBundle.message("command.name.generate.code.for.workspace.entities.in", sourceFolder.name), null)
    } else {
      LOG.info("Not found types for generation")
    }
  }

  private fun loadObjModules(ktClasses: HashMap<String, KtClass>, module: Module): List<CompiledObjModule> {
    val packages = ktClasses.values.mapTo(LinkedHashSet()) { it.containingKtFile.packageFqName.asString() }
    val metaModelProvider = WorkspaceMetaModelProvider.getInstance(module.project)
    return packages.map { metaModelProvider.getObjModule(it, module) }
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

  private fun addInnerDeclarations(ktClass: KtClass, code: GeneratedCode, imports: Imports) {
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

  private const val GENERATED_REGION_START = "//region generated code"
  private const val GENERATED_REGION_END = "//endregion"

  private val ASTNode.isGeneratedRegionStart: Boolean
    get() =
      elementType == KtTokens.EOL_COMMENT && text == GENERATED_REGION_START || firstChildNode?.isGeneratedRegionStart == true

  private val ASTNode.isGeneratedRegionEnd: Boolean
    get() =
      elementType == KtTokens.EOL_COMMENT && text == GENERATED_REGION_END


  private fun createPackageFolderIfMissing(sourceRoot: VirtualFile, sourceFile: VirtualFile, genFolder: VirtualFile): VirtualFile {
    val relativePath = VfsUtil.getRelativePath(sourceFile.parent, sourceRoot, '/')
    return VfsUtil.createDirectoryIfMissing(genFolder, "$relativePath")
  }
}