// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

class WorkspaceModelGenerationAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(PlatformCoreDataKeys.MODULE) ?: return
    val virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (virtualFiles.size != 1 || !virtualFiles[0].isDirectory) return
    val selectedFolder = virtualFiles[0]

    val sourceRoot = getSourceRoot(module, selectedFolder) ?: return
    createGeneratedSourceFolder(module, sourceRoot)
    val packageFolder = createPackageFolder(sourceRoot.file!!, selectedFolder) ?: return
    WriteAction.run<RuntimeException> { CodeWriter.generate(selectedFolder, packageFolder, "org.jetbrains.workspaceModel") }
    println("Selected module ${module.name}")
      //VfsUtilCore.visitChildrenRecursively(sourceRoot, object : VirtualFileVisitor<Unit>() {
          //  override fun visitFile(file: VirtualFile): Boolean {
          //    if (file.extension == "kt" ) {
          //      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return true
          //      if (psiFile is KtFile) {
          //        /*
          //        KotlinAnnotationsIndex.getInstance().get(COMPOSE_PREVIEW_ANNOTATION_NAME, project,
          //                                                 GlobalSearchScope.fileScope(project, vFile)).asSequence()
          //        */
          //        println(psiFile.name)
          //        val importList = psiFile.importList
          //        val children = importList?.children ?: return true
          //        for (child in children) {
          //          child as KtImportDirective
          //          val resolve = child.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() ?: return true
          //          val importedFile = resolve.containingFile ?: return true
          //          println("${child.text} ${importedFile.name} ${importedFile.fileType}")
          //          when (importedFile) {
          //            is KtFile -> {
          //              val `class` = importedFile.classes[0]
          //              //`class`.methods.forEach {
          //              //  println(it.name)
          //              //}
          //              `class`.implementsListTypes.forEach {
          //                println(it.className)
          //              }
          //              println("")
          //            }
          //            is ClsFileImpl -> {
          //              val `class` = importedFile.classes[0]
          //              //`class`.allMethods.forEach {
          //              //  println(it.name)
          //              //}
          //              `class`.implementsListTypes.forEach {
          //                println(it.className)
          //              }
          //              println("")
          //            }
          //            else -> {
          //              error("Unsupported file type")
          //            }
          //          }
          //          println("----------------------------------------")
          //        }
          //      }
          //      //PsiTreeUtil.processElements(psiFile) { psiElement ->
          //      //  if (psiElement is KtClass && psiElement.isInterface() && hasEntityAnnotation(psiElement))  {
          //      //    val className = psiElement.name!!
          //      //    println(className)
          //      //    val packageName = psiElement.fqName.toString().dropLast(className.length + 1)
          //      //    val generator = ModifiableModelGenerator(className, packageName)
          //      //    psiElement.getProperties().forEach { ktProperty ->
          //      //      // TODO:: Add tests for this
          //      //      // TODO:: Add check for Annotation and type nullability
          //      //      if (ktProperty.annotationEntries.isEmpty()) {
          //      //        generator.addProperty(ktProperty.name!!, convertToPropertyType(ktProperty.type()!!),  null)
          //      //      } else if (hasConnectionAnnotation(ktProperty)) {
          //      //        generator.addProperty(ktProperty.name!!, convertToPropertyType(ktProperty.type()!!), getPropertyAnnotation(ktProperty))
          //      //      }
          //      //      ktProperty.isVar
          //      //    }
          //      //    val packageFolder = createPackageFolder(sourceRoot, packageName)
          //      //    generator.generate(packageFolder.canonicalPath!!)
          //      //  }
          //      //  return@processElements true
          //      //}
          //      //println("${file.presentableUrl} ${psiFile.language} ${psiFile.fileType}")
          //    }
          //    return true
          //  }
          //})
  }

  private fun createGeneratedSourceFolder(module: Module, sourceFolder: SourceFolder) {
    // Create gen folder if it doesn't exist
    val generatedFolder = WriteAction.compute<VirtualFile, RuntimeException> {
      VfsUtil.createDirectoryIfMissing(sourceFolder.file?.parent, GENERATED_FOLDER_NAME)
    }

    val moduleRootManager = ModuleRootManager.getInstance(module)
    val modifiableModel = moduleRootManager.modifiableModel
    // Searching for the related content root
    for (contentEntry in modifiableModel.contentEntries) {
      val contentEntryFile = contentEntry.file
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, generatedFolder, false)) {
        // Checking if it already contains generation folder
        val existingGenFolder = contentEntry.sourceFolders.firstOrNull {
          (it.jpsElement.properties as? JavaSourceRootProperties)?.isForGeneratedSources == true &&
          it.file == generatedFolder
        }
        if (existingGenFolder != null) return
        // If it doesn't, create new get folder for the selected content root
        val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true)
        val sourceFolderType = if (sourceFolder.isTestSource) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
        contentEntry.addSourceFolder(generatedFolder, sourceFolderType, properties)
        WriteAction.run<RuntimeException> {
          modifiableModel.commit()
          module.project.save()
        }
        return
      }
    }
    modifiableModel.dispose()
  }

  private fun getSourceRoot(module: Module, selectedFolder: VirtualFile): SourceFolder? {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    for (contentEntry in moduleRootManager.contentEntries) {
      val contentEntryFile = contentEntry.file
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, selectedFolder, false)) {
        val sourceFolder = contentEntry.sourceFolders.firstOrNull { sourceRoot -> VfsUtil.isUnder(selectedFolder, setOf(sourceRoot.file)) }
        if (sourceFolder != null) return sourceFolder
      }
    }
    return null
  }

  private fun createPackageFolder(sourceRoot: VirtualFile, selectedFolder: VirtualFile): VirtualFile? {
    val relativePath = VfsUtil.getRelativePath(selectedFolder, sourceRoot, '/')
    return WriteAction.compute<VirtualFile, RuntimeException> { VfsUtil.createDirectoryIfMissing(sourceRoot.parent, "$GENERATED_FOLDER_NAME/$relativePath") }
  }

  companion object {
    private const val GENERATED_FOLDER_NAME = "gen"
    private val RELATIONSHIP_ANNOTATION = setOf("com.intellij.workspaceModel.storage.generator.annotations.ManyToOne",
                                                "com.intellij.workspaceModel.storage.generator.annotations.OneToMany",
                                                "com.intellij.workspaceModel.storage.generator.annotations.OneToOne")
    private const val ENTITY_ANNOTATION = "com.intellij.workspaceModel.storage.generator.annotations.Entity"
  }
}