// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.*

private val LOG = logger<WorkspaceImplObsoleteInspection>()

class WorkspaceImplObsoleteInspection: LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      if (!klass.isWorkspaceEntity()) return
      val targetApiVersion = calculateTargetApiVersion(klass.project)
      if (targetApiVersion == null) {
        LOG.info("Can't evaluate target API version")
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

  private fun calculateTargetApiVersion(project: Project): Int? {
    val generatorVersions = CodeGeneratorVersions::class.simpleName
    val foundClasses = KotlinClassShortNameIndex.get(generatorVersions!!, project, GlobalSearchScope.allScope(project))
    if (foundClasses.isEmpty()) {
      error("Can't find $generatorVersions")
    }
    val ktClassOrObject = foundClasses.first()
    val fieldName = "API_VERSION"
    val ktDeclaration = ktClassOrObject.declarations.first { it.name == fieldName }
    if (ktDeclaration !is KtProperty) {
      error("Unexpected declaration type for field $fieldName")
    }

    val propertyExpression = ktDeclaration.initializer as? KtConstantExpression
    if (propertyExpression == null) {
      error("Property value should be int constant")
    }
    val elementType = propertyExpression.node.elementType
    if (elementType == KtNodeTypes.INTEGER_CONSTANT) {
      return parseNumericLiteral(propertyExpression.text, elementType)?.toInt()
    }
    return null
  }
}

private class RegenerateWorkspaceModelFix(psiElement: PsiElement) : LocalQuickFixOnPsiElement(psiElement) {
  override fun getText() = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.regenerate.implementation")

  override fun getFamilyName() = name

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = projectFileIndex.getModuleForFile(file.virtualFile)
    WorkspaceModelGenerator.generate(project, module!!)
  }
}