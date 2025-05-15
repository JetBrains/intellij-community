// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.codegen.engine.SKIPPED_TYPES
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class WorkspaceImplGenerationInspection: LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      val superTypeFqn = klass.getWorkspaceModelSuperType()
      if (superTypeFqn == null) {
        return
      }

      val highlightType: ProblemHighlightType
      val descriptionTemplate: String
      if (superTypeFqn == WorkspaceEntity::class.qualifiedName) { //is WorkspaceEntity implementation
        if (!klass.isInterface()) return
        if (klass.name in SKIPPED_TYPES) return
        if (klass.isAbstractEntity()) return
        if (klass.name == "Builder") return
        val foundImplClasses = KotlinClassShortNameIndex["${klass.name}Impl", klass.project, GlobalSearchScope.allScope(klass.project)]
        if (foundImplClasses.isEmpty()) {
          descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.absent.implementation")
          highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
          descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.regenerate.implementation")
          highlightType = ProblemHighlightType.INFORMATION
        }
      } else {
        descriptionTemplate = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.collect.class.metadata")
        highlightType = ProblemHighlightType.INFORMATION
      }

      holder.registerProblem(
        klass.nameIdentifier!!, descriptionTemplate, highlightType,
        GenerateWorkspaceModelFix(klass.nameIdentifier!!)
      )
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