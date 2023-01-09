// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionProperty
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getCapitalizedAccessorName
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import java.nio.file.Path

class GradleVersionCatalogGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    if (!Registry.`is`(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) {
      return null
    }
    if (sourceElement == null) {
      return null
    }
    val resolved = sourceElement.parentOfType<GrReferenceExpression>()?.resolve() ?: return null
    val settingsFile = getSettingsFile(sourceElement.project) ?: return null
    val resolveVisitor = GroovySettingsFileResolveVisitor(resolved)
    settingsFile.accept(resolveVisitor)
    return resolveVisitor.resolveTarget?.let { arrayOf(it) }
  }
}

private fun getSettingsFile(project: Project) : GroovyFileBase? {
  val projectData = ProjectDataManager.getInstance().getExternalProjectsData(project,
                                                                             GradleConstants.SYSTEM_ID).mapNotNull { it.externalProjectStructure }
  for (projectDatum in projectData) {
    val settings = Path.of(projectDatum.data.linkedExternalProjectPath).resolve(GradleConstants.SETTINGS_FILE_NAME).let {
      VfsUtil.findFile(it, false)
    }?.let { PsiManager.getInstance(project).findFile(it) }?.asSafely<GroovyFileBase>()
    return settings
  }
  return null
}

private class GroovySettingsFileResolveVisitor(val element : PsiElement) : GroovyRecursiveElementVisitor() {
  var resolveTarget : PsiElement? = null
  val accessorName = element.asSafely<PsiMethod>()?.takeIf { it.returnType?.resolve()?.qualifiedName == GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER }?.let(::getCapitalizedAccessorName)

  override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
    val method = methodCallExpression.resolveMethod()
    if (element is GradleExtensionProperty && element.name == method?.name && method.returnType?.equalsToText(GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) == true) {
      resolveTarget = methodCallExpression
      return
    }
    if (accessorName != null && method?.containingClass?.qualifiedName == GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) {
      val definedName = methodCallExpression.argumentList.expressionArguments.firstOrNull()
      val definedNameValue = GroovyConstantExpressionEvaluator.evaluate(definedName).asSafely<String>() ?: return super.visitMethodCallExpression(methodCallExpression)
      val longName = definedNameValue.split("_", ".", "-").joinToString("", transform = GroovyPropertyUtils::capitalize)
      if (longName == accessorName) {
        resolveTarget = methodCallExpression
        return
      }
    }
    super.visitMethodCallExpression(methodCallExpression)
  }
}


