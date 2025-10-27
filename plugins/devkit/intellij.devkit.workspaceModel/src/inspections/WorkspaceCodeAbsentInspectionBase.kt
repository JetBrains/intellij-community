// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.workspaceModel.codegen.engine.SKIPPED_TYPES
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class WorkspaceCodeAbsentInspectionBase : WorkspaceInspectionBase() {
  protected abstract fun belongToSameModule(ktClass: KtClassOrObject, otherKtClass: KtClassOrObject): Boolean
  
  private fun entitySourceIsPresentInMetadata(ktClass: KtClassOrObject): Boolean {
    val jvmName = KotlinPsiHeuristics.getJvmName(ktClass) ?: return true
    val searchScope = getModuleSearchScope(ktClass)
    val moduleMetadata = KotlinClassShortNameIndex["MetadataStorageImpl", ktClass.project, searchScope]
    val metadataFile: PsiFile = moduleMetadata.firstOrNull { metadataObject ->
      belongToSameModule(metadataObject, ktClass) && metadataObject.getMatchingSuperTypes { superType ->
        superType.fqName?.asString() == MetadataStorageBase::class.qualifiedName
      }.any()
    }?.containingFile ?: return false
    // String.escapeDollar codegen/impl/metadata/StringExtensions.kt
    val workspaceMetaName = jvmName.replace("$", "\\$")
    // TODO: improve
    val jvmNameOccurrence = metadataFile.getFileDocument().getImmutableCharSequence().indexOf(workspaceMetaName)
    return jvmNameOccurrence != -1
  }

  private fun findSuperSourceAbsentInMetadata(ktClass: KtClassOrObject, visited: MutableSet<KtClass>): String? {
    for (superType in ktClass.superTypeListEntries) {
      val resolvedSuper = superType.typeReference?.resolveToKtClass() ?: continue
      if (!visited.add(resolvedSuper)) continue
      if (resolvedSuper.isWorkspaceEntitySource()) {
        if (!entitySourceIsPresentInMetadata(resolvedSuper)) return resolvedSuper.name
        val absentSuper = findSuperSourceAbsentInMetadata(resolvedSuper, visited)
        if (absentSuper != null) return absentSuper
      }
    }
    return null
  }

  private fun processWorkspaceEntityDeclaration(klass: KtClass, holder: ProblemsHolder) {
    val psiElementForHighlighting = klass.getPsiElementForHighlighting() ?: return
    val highlightType: ProblemHighlightType
    val descriptionTemplate: String

    if (klass.name in SKIPPED_TYPES) return
    if (klass.isAbstractEntity()) return
    val foundImpl = findWorkspaceEntityImplementation(klass, getModuleSearchScope(klass))
    if (foundImpl == null) {
      descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.absent.implementation")
      highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }
    else {
      descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.regenerate.implementation")
      highlightType = ProblemHighlightType.INFORMATION
    }

    holder.registerProblem(
      psiElementForHighlighting, descriptionTemplate, highlightType,
      GenerateWorkspaceModelFix(psiElementForHighlighting)
    )
  }

  private fun processEntitySource(klass: KtClassOrObject, holder: ProblemsHolder) {
    val psiElementForHighlighting = klass.getPsiElementForHighlighting() ?: return
    val highlightType: ProblemHighlightType
    val descriptionTemplate: String

    if (!entitySourceIsPresentInMetadata(klass)) {
      descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.absent.source.metadata")
      highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }
    else {
      val absentSuper = findSuperSourceAbsentInMetadata(klass, mutableSetOf()) ?: return
      descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.absent.parent.source.metadata", absentSuper)
      highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }

    holder.registerProblem(
      psiElementForHighlighting, descriptionTemplate, highlightType,
      GenerateWorkspaceModelFix(psiElementForHighlighting)
    )
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
    override fun visitClassOrObject(klass: KtClassOrObject) {
      val superTypesFqns = klass.getWorkspaceSupers()
      if (superTypesFqns.isEmpty()) {
        return
      }

      if (klass is KtClass && klass.isWorkspaceEntityDeclaration()) {
        processWorkspaceEntityDeclaration(klass, holder)
      }
      else if (klass.isWorkspaceEntitySource()) {
        processEntitySource(klass, holder)
      }
    }
  }

  private class GenerateWorkspaceModelFix(psiElement: PsiElement) : LocalQuickFixOnPsiElement(psiElement) {
    override fun getText() = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.generate.implementation")

    override fun getFamilyName() = name

    override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
      val module = projectFileIndex.getModuleForFile(psiFile.virtualFile)
      WorkspaceModelGenerator.getInstance(project).generate(module!!)
    }

    override fun startInWriteAction(): Boolean = false
  }
}