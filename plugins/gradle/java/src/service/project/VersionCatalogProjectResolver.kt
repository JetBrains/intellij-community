// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import com.intellij.util.io.exists
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.getRootGradleProjectPath
import org.jetbrains.plugins.gradle.util.GradleConstants.SETTINGS_FILE_NAME
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator
import java.nio.file.Path

fun GroovyFileBase.getVersionCatalogsData(): Map<String, Path> {
  if (this.name != SETTINGS_FILE_NAME) {
    return emptyMap()
  }
  return CachedValuesManager.getCachedValue(this) {
    val result = computeVersionCatalogsData(this)
    CachedValueProvider.Result(result, PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun computeVersionCatalogsData(settingsFile: GroovyFileBase): Map<String, Path> {
  val projectRootLocation = settingsFile.getRootGradleProjectPath()?.let(Path::of) ?: return emptyMap()
  val tracker = GradleStructureTracker(projectRootLocation)
  runReadAction {
    settingsFile.accept(tracker)
  }
  val mapping = tracker.catalogMapping
  val defaultLibsFile = projectRootLocation.resolve("gradle").resolve("libs.versions.toml")
  if (defaultLibsFile.exists()) {
    val libsValue = mapping["libs"]
    if (libsValue == null) {
      mapping["libs"] = defaultLibsFile
    }
  }
  return mapping
}

/**
 * This is a temporary and hacky implementation aimed to support navigation to version catalogs.
 * We need a proper API from Gradle to support this functionality without these unreliable AST-based heuristics.
 */
private class GradleStructureTracker(val projectRoot: Path) : GroovyRecursiveElementVisitor() {

  val catalogMapping: MutableMap<String, Path> = mutableMapOf()

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
        catalogMapping[name] = file
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
