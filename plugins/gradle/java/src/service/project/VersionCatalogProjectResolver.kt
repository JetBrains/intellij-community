// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import com.intellij.util.io.exists
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.data.VersionCatalogTomlFilesModel
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator
import java.nio.file.Path

class VersionCatalogProjectResolver : AbstractProjectResolverExtension() {

  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {
    val settingsFilePath = getGradleSettingsFile(ideProject)
    val virtualFile = VfsUtil.findFile(settingsFilePath, false) ?: return super.populateProjectExtraModels(gradleProject, ideProject)
    val projectRoot = Path.of(ideProject.data.linkedExternalProjectPath)
    val project = ProjectUtil.findProject(projectRoot) ?: return super.populateProjectExtraModels(gradleProject, ideProject)
    DumbService.getInstance(project).runWhenSmart {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile).castSafelyTo<GroovyFileBase>() ?: return@runWhenSmart
      val tracker = GradleStructureTracker(projectRoot)
      runReadAction {
        psiFile.accept(tracker)
      }
      val mapping = tracker.catalogMapping
      val defaultLibsFile = projectRoot.resolve("gradle/libs.versions.toml")
      if (defaultLibsFile.exists()) {
        val libsValue = mapping["libs"]
        if (libsValue == null) {
          mapping["libs"] = defaultLibsFile.toString()
        }
      }
      ideProject.createChild(VersionCatalogTomlFilesModel.KEY, VersionCatalogTomlFilesModel(mapping))
    }
    super.populateProjectExtraModels(gradleProject, ideProject)
  }

  private fun getGradleSettingsFile(ideProject: DataNode<ProjectData>): Path {
    return Path.of(ideProject.data.linkedExternalProjectPath).resolve(GradleConstants.SETTINGS_FILE_NAME)
  }
}

/**
 * This is a temporary and hacky implementation aimed to support navigation to version catalogs.
 * We need a proper API from Gradle to support this functionality without these unreliable AST-based heuristics.
 */
private class GradleStructureTracker(val projectRoot: Path) : GroovyRecursiveElementVisitor() {

  val catalogMapping: MutableMap<String, String> = mutableMapOf()

  override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
    val method = methodCallExpression.resolveMethod() ?: return super.visitMethodCallExpression(methodCallExpression)
    if (method.name == "from" && method.containingClass?.qualifiedName == GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) {
      val container = methodCallExpression.parentsOfType<GrMethodCallExpression>().find {
        it.resolveMethod()?.returnType?.equalsToText(GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) == true
      }
      val name = container?.resolveMethod()?.name
      if (container == null || name == null) {
        return super.visitMethodCallExpression(methodCallExpression)
      }
      val file = resolveDependencyNotation(methodCallExpression.expressionArguments.singleOrNull(), projectRoot)
      if (file != null) {
        catalogMapping[name] = file.toString()
      }
    }
    super.visitMethodCallExpression(methodCallExpression)
  }

}

/**
 * @return filename of toml catalog
 */
private fun resolveDependencyNotation(element: PsiElement?, root: Path): Path? {
  element ?: return null
  if (element is GrMethodCallExpression && element.resolveMethod()?.name == "files") {
    val argument = element.argumentList.expressionArguments.firstOrNull()
    return GroovyConstantExpressionEvaluator.evaluate(argument)?.castSafelyTo<String>()?.let(root::resolve)?.takeIf(Path::exists)
  }
  return null
}
