// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.codegen.deft.model.KtObjModule
import com.intellij.workspaceModel.codegen.engine.GeneratedCode
import com.intellij.workspaceModel.codegen.engine.impl.CodeGeneratorImpl
import com.intellij.workspaceModel.codegen.model.convertToObjModules
import com.intellij.workspaceModel.storage.*
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.children

val SKIPPED_TYPES = setOf(WorkspaceEntity::class.simpleName,
                          ReferableWorkspaceEntity::class.simpleName,
                          ModifiableWorkspaceEntity::class.simpleName,
                          ModifiableReferableWorkspaceEntity::class.simpleName,
                          WorkspaceEntityWithPersistentId::class.simpleName)

private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresWriteLock
  fun generate(project: Project, sourceFolder: VirtualFile,  keepUnknownFields: Boolean, targetFolderGenerator: () -> VirtualFile?) {
    val documentManager = FileDocumentManager.getInstance()
    val ktSrcs = mutableListOf<Pair<VirtualFile, Document>>()
    val fileMapping = mutableMapOf<String, VirtualFile>()
    val ktClasses = HashMap<String, KtClass>()
    VfsUtilCore.processFilesRecursively(sourceFolder) {
      if (it.extension == "kt") {
        val document = documentManager.getDocument(it) ?: return@processFilesRecursively true
        ktSrcs.add(it to document)
        fileMapping[it.name] = it
        val ktFile = PsiManager.getInstance(project).findFile(it) as? KtFile?
        ktFile?.declarations?.filterIsInstance<KtClass>()?.filter { clazz -> clazz.name != null }?.associateByTo(ktClasses) { clazz ->
          clazz.fqName!!.asString()
        }
      }
      return@processFilesRecursively true
    }

    val module = KtObjModule(project, keepUnknownFields = keepUnknownFields)
    ktSrcs.forEach { (vfu, document) ->
      module.addPsiFile(vfu.name, vfu) { document.text }
    }
    val result = module.build()
    val objModules = convertToObjModules(result.typeDefs, result.simpleTypes, result.extFields)
    val codeGenerator = CodeGeneratorImpl()
    val generated = objModules.flatMap { codeGenerator.generate(it) }
    if (generated.isNotEmpty()) {
      val genFolder = targetFolderGenerator.invoke()
      if (genFolder == null) {
        LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
        return
      }

      CommandProcessor.getInstance().executeCommand(project, Runnable {
        ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread("Generating Code", project, null) { indicator ->
          indicator.text = "Writing code"
          val psiFactory = KtPsiFactory(project)
          ktClasses.values.flatMapTo(HashSet()) { listOfNotNull(it.containingFile.node, it.body?.node) }.forEach {
            removeChildrenInGeneratedRegions(it)
          }
          val topLevelDeclarations = MultiMap.create<KtFile, Pair<KtClass, List<KtDeclaration>>>()
          val generatedFiles = ArrayList<KtFile>()
          generated.forEach { code ->
            val ktClass = ktClasses[code.target.javaFullName]!!
            addInnerDeclarations(ktClass, code)
            val topLevelCode = code.topLevelCode
            if (topLevelCode != null) {
              val ktFile = ktClass.containingKtFile
              val declarations = psiFactory.createFile(code.topLevelCode).declarations
              topLevelDeclarations.putValue(ktFile, ktClass to declarations)
            }
            val implementationClassText = code.implementationClass
            if (implementationClassText != null) {
              val sourceFile = ktClass.containingFile.virtualFile
              val packageFolder = createPackageFolderIfMissing(sourceFolder, sourceFile, genFolder)
              val targetDirectory = PsiManager.getInstance(project).findDirectory(packageFolder)!!
              val implFile = psiFactory.createFile("${code.target.name}Impl.kt", implementationClassText)
              ktClass.containingKtFile.importDirectives.forEach { import ->
                implFile.importList!!.add(psiFactory.createNewLine())
                implFile.importList!!.add(import)
              }
              targetDirectory.findFile(implFile.name)?.delete()
              //todo remove other old generated files
              generatedFiles.add(targetDirectory.add(implFile) as KtFile)
            }
          }

          indicator.text = "Formatting generated code"
          generatedFiles.forEachIndexed { i, file ->
            indicator.fraction = 0.5 * i / generatedFiles.size
            val processed = ShortenReferences.DEFAULT.process(file)
            CodeStyleManager.getInstance(project).reformat(processed)
          }
          topLevelDeclarations.entrySet().forEachIndexed { i, (file, placeAndDeclarations) ->
            indicator.fraction = 0.5 + 0.25 * i / topLevelDeclarations.size()
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
            for (declaration in addedElements) {
              ShortenReferences.DEFAULT.process(declaration)
            }
          }

          val filesWithGeneratedRegions = ktClasses.values.groupBy { it.containingFile }.toList()
          filesWithGeneratedRegions.forEachIndexed { i, (file, classes) ->
            indicator.fraction = 0.75 + 0.25 * i / filesWithGeneratedRegions.size
            reformatCodeInGeneratedRegions(file, classes.mapNotNull { it.body?.node } + listOf(file.node))
          }

        }
      }, "Generate Code for Workspace Entities in '${sourceFolder.name}'", null)
    } else {
      LOG.info("Not found types for generation")
    }
  }

  private fun addInnerDeclarations(ktClass: KtClass, code: GeneratedCode) {
    val psiFactory = KtPsiFactory(ktClass.project)
    val builderInterface = ShortenReferences.DEFAULT.process(ktClass.addDeclaration(psiFactory.createClass(code.builderInterface)))
    val companionObject = ShortenReferences.DEFAULT.process(ktClass.addDeclaration(psiFactory.createObject(code.companionObject)))
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
    //CodeStyleManager.getInstance(file.project).reformat(file)
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