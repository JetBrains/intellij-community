// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val LOG = logger<WorkspaceImplObsoleteInspectionBase>()

abstract class WorkspaceImplObsoleteInspectionBase : WorkspaceInspectionBase() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      val psiElementForHighlighting = klass.getPsiElementForHighlighting() ?: return
      if (!klass.isWorkspaceEntityDeclaration()) return
      val foundImplClass = findWorkspaceEntityImplementation(klass, getModuleSearchScope(klass))
      if (foundImplClass == null) return

      val targetApiVersion = calculateTargetApiVersion(klass.resolveScope, klass.project)
      if (targetApiVersion == null) {
        LOG.info("Can't evaluate target API version for ${klass.name}")
        return
      }


      val implApiVersion = foundImplClass.getApiVersion()
      if (implApiVersion == targetApiVersion) return
      holder.registerProblem(psiElementForHighlighting, DevKitWorkspaceModelBundle.message("inspection.workspace.msg.obsolete.implementation"),
                             RegenerateWorkspaceModelFix(psiElementForHighlighting))
    }
  }

  private fun calculateTargetApiVersion(scope: GlobalSearchScope, project: Project): Int? {
    val generatorVersionsClass = JavaPsiFacade.getInstance(project).findClass(CodeGeneratorVersions::class.java.name, scope) ?: return null
    val versionField = generatorVersionsClass.findFieldByName("API_VERSION_INTERNAL", false) ?: return null
    return (versionField.initializer as? PsiLiteralExpression)?.value as? Int
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
}

private fun KtClass.getGeneratedAnnotationVersion(annotationFqName: String): Int? {
  val annotation = findAnnotation(annotationFqName) ?: return null
  if (annotation.valueArguments.size != 1) {
    LOG.warn("Annotation $annotationFqName at $name should contain exactly one argument")
    return null
  }
  val argumentExpression = annotation.valueArguments[0].getArgumentExpression() as? KtConstantExpression
  val elementType = argumentExpression?.node?.elementType
  if (elementType != KtNodeTypes.INTEGER_CONSTANT) {
    LOG.warn("Annotation parameter of $annotationFqName at $name should be an int constant")
    return null
  }
  return parseNumericLiteral(argumentExpression.text, elementType)?.toInt()
}

private fun KtClass.getApiVersion(): Int? {
  val annotationFqName = GeneratedCodeApiVersion::class.qualifiedName!!
  return getGeneratedAnnotationVersion(annotationFqName)
}

private fun KtClass.getImplVersion(): Int? {
  val annotationFqName = GeneratedCodeImplVersion::class.simpleName!!
  return getGeneratedAnnotationVersion(annotationFqName)
}