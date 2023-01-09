// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.service.resolve.transformation.GradleDecoratedLightPsiClass
import org.jetbrains.plugins.gradle.util.GradleConstants.EXTENSION
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

val projectTypeKey: Key<GradleProjectAwareType> = Key.create("gradle.current.project")
val saveProjectType: PatternCondition<GroovyMethodResult> = object : PatternCondition<GroovyMethodResult>("saveProjectContext") {
  override fun accepts(result: GroovyMethodResult, context: ProcessingContext?): Boolean {
    // Given the closure matched some method,
    // we want to determine what we know about this Project.
    // This PatternCondition just saves the info into the ProcessingContext.
    context?.put(projectTypeKey, result.candidate?.receiverType as? GradleProjectAwareType)
    return true
  }
}

val DELEGATED_TYPE: Key<Boolean> = Key.create("gradle.delegated.type")

@JvmField
val DECLARATION_ALTERNATIVES: Key<List<String>> = Key.create("gradle.declaration.alternatives")

/**
 * @author Vladislav.Soroka
 */
internal fun PsiClass?.isResolvedInGradleScript() = this is GroovyScriptClass && this.containingFile.isGradleScript()

internal fun PsiFile?.isGradleScript() = this?.originalFile?.virtualFile?.extension == EXTENSION

private val PsiElement.module get() = containingFile?.originalFile?.virtualFile?.let {
  ProjectFileIndex.getInstance(project).getModuleForFile(it)
}

internal fun PsiElement.getLinkedGradleProjectPath() : String? {
  return ExternalSystemApiUtil.getExternalProjectPath(module)
}

internal fun PsiElement.getRootGradleProjectPath() : String? {
  return ExternalSystemApiUtil.getExternalRootProjectPath(module)
}

internal fun decoratePsiType(type: PsiType) : PsiType =
  if (type is PsiClassType) decoratePsiClassType(type) else type

/**
 * @see org.jetbrains.plugins.gradle.service.resolve.transformation.GradleDecoratedLightPsiClass
 */
internal fun decoratePsiClassType(type: PsiClassType) : PsiClassType {
  val resolveResult = type.resolveGenerics()
  val resolvedClass = resolveResult.element ?: return type
  val decorated = GradleDecoratedLightPsiClass(resolvedClass)
  return resolveResult.substitutor.substitute(decorated.type()).asSafely<PsiClassType>() ?: return type
}