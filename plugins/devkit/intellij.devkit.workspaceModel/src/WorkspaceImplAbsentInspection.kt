// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.codegen.engine.SKIPPED_TYPES
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.*

class WorkspaceImplAbsentInspection: LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      if (!klass.isWorkspaceEntity()) return
      if (klass.name in SKIPPED_TYPES) return
      if (klass.isAbstractEntity()) return
      if (klass.name == "Builder") return
      val foundImplClasses = KotlinClassShortNameIndex.get("${klass.name}Impl", klass.project, GlobalSearchScope.allScope(klass.project))
      if (!foundImplClasses.isEmpty()) return
      holder.registerProblem(klass.nameIdentifier!!, DevKitWorkspaceModelBundle.message("inspection.workspace.absent.model.display.name"),
                             GenerateWorkspaceModelFix(klass.nameIdentifier!!))
    }
  }
}

private class GenerateWorkspaceModelFix(psiElement: PsiElement) : LocalQuickFixOnPsiElement(psiElement) {
  override fun getText() = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.generate.implementation")

  override fun getFamilyName() = name

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = projectFileIndex.getModuleForFile(file.virtualFile)
    WorkspaceModelGenerator.getInstance(project).generate(module!!)
  }
}