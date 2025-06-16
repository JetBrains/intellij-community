// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val LOG = logger<WorkspaceImplObsoleteInspection>()

internal class WorkspaceImplObsoleteInspection: LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      if (!klass.isWorkspaceEntity()) return
      val targetApiVersion = calculateTargetApiVersion(klass.resolveScope, klass.project)
      if (targetApiVersion == null) {
        LOG.info("Can't evaluate target API version for ${klass.name}")
        return
      }
      if (klass.name == "Builder") return
      val foundImplClasses = KotlinClassShortNameIndex.get("${klass.name}Impl", klass.project, GlobalSearchScope.allScope(klass.project))
      if (foundImplClasses.isEmpty()) return
      val implClass = foundImplClasses.first()
      val apiVersion = (implClass as? KtClass)?.getApiVersion()
      if (apiVersion == targetApiVersion) return
      holder.registerProblem(klass.nameIdentifier!!, DevKitWorkspaceModelBundle.message("inspection.workspace.msg.obsolete.implementation"),
                             RegenerateWorkspaceModelFix(klass.nameIdentifier!!))
    }
  }

  private fun calculateTargetApiVersion(scope: GlobalSearchScope, project: Project): Int? {
    val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, scope) ?: return null
    val versionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false) ?: return null
    return (versionField.initializer as? PsiLiteralExpression)?.value as? Int
  }
}

private class RegenerateWorkspaceModelFix(psiElement: PsiElement) : LocalQuickFixOnPsiElement(psiElement) {
  override fun getText() = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.regenerate.implementation")

  override fun getFamilyName() = name

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = projectFileIndex.getModuleForFile(psiFile.virtualFile)
    WorkspaceModelGenerator.getInstance(project).generate(module!!)
  }
}