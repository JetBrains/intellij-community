// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.psi

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.workspace.model.generate
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import kotlin.io.path.name
import kotlin.io.path.pathString

class WorkspaceGenerateModifiableEntitiesAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val module = event.getData(PlatformCoreDataKeys.MODULE) ?: return
    val virtualFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (virtualFiles.size != 1 || !virtualFiles[0].isDirectory) return
    val selectedFolder = virtualFiles[0]
    val sourceRoot = getSourceRoot(module, selectedFolder)
    WriteAction.run<RuntimeException> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      val generatedFolder = WriteAction.compute<VirtualFile, RuntimeException> {
        VfsUtil.createDirectoryIfMissing(sourceRoot!!.parent, "gen")
      }
      createGenerationFolder(module, moduleRootManager, generatedFolder)
    }

    val genFolder = createPackageFolderPath(sourceRoot!!, selectedFolder)
    generate(selectedFolder, genFolder!!)

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

  private fun getSourceRoot(module: Module, selectedFolder: VirtualFile): VirtualFile? {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    return moduleRootManager.getSourceRoots(false).firstOrNull { sourceRoot ->
      VfsUtil.isUnder(selectedFolder, setOf(sourceRoot))
    }
  }

  private fun createPackageFolderPath(sourceRoot: VirtualFile,selectedFolder: VirtualFile): VirtualFile? {
    val relativePath = sourceRoot.toNioPath().relativize(selectedFolder.toNioPath())
    return WriteAction.compute<VirtualFile, RuntimeException> { VfsUtil.createDirectoryIfMissing(sourceRoot.parent, "gen/$relativePath")}
  }

  private fun createGenerationFolder(module: Module, moduleRootManager: ModuleRootManager, generatedFolder: VirtualFile) {
    val modifiableModel = moduleRootManager.modifiableModel
    val contentEntries = modifiableModel.contentEntries
    for (contentEntry in contentEntries) {
      val contentEntryFile = contentEntry.file
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, generatedFolder, false)) {
        val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true)
        contentEntry.addSourceFolder(generatedFolder, JavaSourceRootType.SOURCE, properties)
        WriteAction.run<RuntimeException> {
          modifiableModel.commit()
          module.project.save()
        }
        return
      }
    }
  }

  companion object {
    private val RELATIONSHIP_ANNOTATION = setOf("com.intellij.workspaceModel.storage.generator.annotations.ManyToOne",
                                                "com.intellij.workspaceModel.storage.generator.annotations.OneToMany",
                                                "com.intellij.workspaceModel.storage.generator.annotations.OneToOne")
    private const val ENTITY_ANNOTATION = "com.intellij.workspaceModel.storage.generator.annotations.Entity"
  }
}
