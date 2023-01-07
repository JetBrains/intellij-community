// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.EXTENSION
import org.jetbrains.plugins.gradle.util.PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.getGradleUserHomePropertiesPath
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import java.nio.file.Path

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

internal fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> = sequence {
  val externalRootProjectPath = place.getRootGradleProjectPath() ?: return@sequence
  val userHomePropertiesFile = getGradleUserHomePropertiesPath()?.parent?.toString()?.getGradlePropertiesFile(place.project)
  if (userHomePropertiesFile != null) {
    yield(userHomePropertiesFile)
  }
  val projectRootPropertiesFile = externalRootProjectPath.getGradlePropertiesFile(place.project)
  if (projectRootPropertiesFile != null) {
    yield(projectRootPropertiesFile)
  }
  val localSettings = GradleLocalSettings.getInstance(place.project)
  val installationDirectoryPropertiesFile = localSettings.getGradleHome(externalRootProjectPath)?.getGradlePropertiesFile(place.project)
  if (installationDirectoryPropertiesFile != null) {
    yield(installationDirectoryPropertiesFile)
  }
}

private fun String.getGradlePropertiesFile(project: Project): PropertiesFile? {
  val file = VfsUtil.findFile(Path.of(this), false)?.findChild(PROPERTIES_FILE_NAME)
  return file?.let { PsiUtilCore.getPsiFile(project, it) }.asSafely<PropertiesFile>()
}
