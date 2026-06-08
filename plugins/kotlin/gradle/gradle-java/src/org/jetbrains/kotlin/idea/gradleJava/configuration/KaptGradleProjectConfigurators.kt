// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.compiler.CompilerConfiguration
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.configuration.AbstractKotlinCompilerProjectPostConfigurator
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.psi.KtFile

private const val KAPT_PLUGIN_ID = "kapt"
private const val KAPT_GRADLE_PLUGIN_NAME = "kotlin.kapt"
private const val KAPT_KEEP_JAVAC_PROCESSORS_OPTION = "keepJavacAnnotationProcessors = true"
private val PROCESSOR_PATH_SEPARATOR: String = System.getProperties().getProperty("path.separator")

internal const val LOMBOK_FQN: String = "lombok.Lombok"

class KaptGradleKotlinCompilerPluginProjectConfigurator : AbstractGradleKotlinCompilerPluginProjectConfigurator() {
  override val kotlinCompilerPluginId: String = KAPT_PLUGIN_ID

  override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
    if (forKotlinDsl) "kotlin(\"kapt\")" else "id \"org.jetbrains.kotlin.kapt\""

  override fun PsiFile.addCustomization(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
    configureKaptDependenciesIfNeeded(changedFiles)

    if (!sourceModule.hasLombokDependency()) return

    GradleBuildScriptSupport.getManipulator(this).configurePluginOptions(
      KAPT_PLUGIN_ID,
      changedFiles,
      KAPT_KEEP_JAVAC_PROCESSORS_OPTION,
    )
  }
}

private fun PsiFile.configureKaptDependenciesIfNeeded(changedFiles: ChangedConfiguratorFiles) {
  if (this !is KtFile) return

  val psiDocumentManager = PsiDocumentManager.getInstance(project)
  val document = psiDocumentManager.getDocument(this) ?: return
  psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
  val fileText = document.text
  val processorDependencyMatches = PROCESSOR_DEPENDENCY_REGEX.findAll(fileText)
    .mapNotNull { it.toKaptProcessorDependency() }
    .toList()
  val kaptDependencies = KAPT_DEPENDENCY_REGEX.findAll(fileText)
    .map { KaptDependency(it.groupValues[1], it.groupValues[2]) }
    .toSet()
  val dependenciesToAdd = processorDependencyMatches
    .distinctBy { it.kaptConfiguration to it.notation }
    .filterNot { KaptDependency(it.kaptConfiguration, it.notation) in kaptDependencies }
  if (dependenciesToAdd.isEmpty()) return

  val lastProcessorMatch = dependenciesToAdd.last().match
  val insertOffset = fileText.indexOf('\n', lastProcessorMatch.range.last + 1).takeIf { it >= 0 } ?: fileText.length
  val indent = lastProcessorMatch.groupValues[1]
  val dependencyLines = dependenciesToAdd.joinToString(separator = "") { dependency ->
    "\n$indent${kaptDependencyNotation(dependency.kaptConfiguration, dependency.notation)}"
  }
  changedFiles.storeOriginalFileContent(this)
  document.insertString(insertOffset, dependencyLines)
  psiDocumentManager.commitDocument(document)
}

class KaptGradleProjectPostConfigurator : AbstractKotlinCompilerProjectPostConfigurator(KAPT_PLUGIN_ID) {
  override fun isApplicable(module: Module): Boolean =
    module.isGradleModule &&
    compilerPluginProjectConfigurators(module).isNotEmpty() &&
    !module.hasKaptGradlePluginConfigured() &&
    module.hasNonLombokAnnotationProcessor()
}

internal fun Module.hasLombokDependency(): Boolean =
  JavaLibraryUtil.hasLibraryClass(this, LOMBOK_FQN)

internal fun Module.hasNonLombokAnnotationProcessor(): Boolean {
  val annotationProcessingConfiguration = CompilerConfiguration.getInstance(project).getAnnotationProcessingConfiguration(this)
  if (annotationProcessingConfiguration.isEnabled) {
    val processors = annotationProcessingConfiguration.processors
    if (processors.any { !it.startsWith("lombok.") }) return true

    val processorPath = annotationProcessingConfiguration.processorPath
      .split(PROCESSOR_PATH_SEPARATOR)
      .filter { it.isNotBlank() }
    if (processorPath.any { !it.isLombokProcessorPath() }) return true
  }

  return KNOWN_NON_LOMBOK_PROCESSOR_CLASSES.any { JavaLibraryUtil.hasLibraryClass(this, it) }
}

internal fun Module.hasKaptGradlePluginConfigured(): Boolean {
  val buildScript = getBuildScriptPsiFile() ?: return false
  val manipulator = GradleBuildScriptSupport.getManipulator(buildScript)
  return manipulator.isConfigured(kaptPluginExpression(buildScript is KtFile)) ||
         manipulator.isConfiguredWithOldSyntax(KAPT_GRADLE_PLUGIN_NAME) ||
         manipulator.isConfiguredWithOldSyntax("kotlin-kapt")
}

internal fun PsiFile.configureKaptForLombokIfNeeded(sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
  if (!sourceModule.hasKaptGradlePluginConfigured()) return

  GradleBuildScriptSupport.getManipulator(this).configurePluginOptions(
    KAPT_PLUGIN_ID,
    changedFiles,
    KAPT_KEEP_JAVAC_PROCESSORS_OPTION,
  )
}

internal fun PsiFile.configureKotlinLombokConfigIfNeeded(sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
  val configFile = sourceModule.findLombokConfigFile(this) ?: return
  val relativePath = VfsUtilCore.getRelativePath(configFile, virtualFile.parent, '/') ?: return

  GradleBuildScriptSupport.getManipulator(this).configurePluginOptions(
    "kotlinLombok",
    changedFiles,
    "lombokConfigurationFile(file(\"$relativePath\"))",
  )
}

private fun Module.findLombokConfigFile(buildScript: PsiFile) =
  buildScript.virtualFile.parent.findChild("lombok.config")
    ?: project.getTopLevelBuildScriptPsiFile()?.virtualFile?.parent?.findChild("lombok.config")

private fun String.isLombokProcessorPath(): Boolean =
  substringAfterLast('/').substringAfterLast('\\').contains("lombok", ignoreCase = true)

private fun String.isLombokDependencyNotation(): Boolean =
  split(':').getOrNull(1) == "lombok" || contains("lombok", ignoreCase = true)

private fun MatchResult.toKaptProcessorDependency(): KaptProcessorDependency? {
  val sourceConfiguration = GradleProcessorDependencyConfiguration.byName(groupValues[2]) ?: return null
  val notation = groupValues[3]
  if (notation.isLombokDependencyNotation()) return null
  if (!sourceConfiguration.acceptsAnyProcessor) {
    val processorPath = GradleProcessorPath.of(notation) ?: return null
    if (processorPath !in KNOWN_PROCESSOR_ARTIFACTS) return null
  }
  return KaptProcessorDependency(
    match = this,
    kaptConfiguration = sourceConfiguration.kaptConfiguration,
    notation = notation,
  )
}

private fun kaptPluginExpression(forKotlinDsl: Boolean): String =
  if (forKotlinDsl) "kotlin(\"kapt\")" else "id \"org.jetbrains.kotlin.kapt\""

private fun kaptDependencyNotation(configuration: String, dependency: String): String =
  "$configuration(\"$dependency\")"

private data class KaptProcessorDependency(val match: MatchResult, val kaptConfiguration: String, val notation: String)

private data class KaptDependency(val configuration: String, val notation: String)

private enum class GradleProcessorDependencyConfiguration(
  val dependencyConfiguration: String,
  val kaptConfiguration: String,
  val acceptsAnyProcessor: Boolean,
) {
  ANNOTATION_PROCESSOR("annotationProcessor", "kapt", acceptsAnyProcessor = true),
  TEST_ANNOTATION_PROCESSOR("testAnnotationProcessor", "kaptTest", acceptsAnyProcessor = true),
  IMPLEMENTATION("implementation", "kapt", acceptsAnyProcessor = false),
  TEST_IMPLEMENTATION("testImplementation", "kaptTest", acceptsAnyProcessor = false);

  companion object {
    private val byDependencyConfiguration = entries.associateBy { it.dependencyConfiguration }

    fun byName(name: String): GradleProcessorDependencyConfiguration? =
      byDependencyConfiguration[name]
  }
}

private data class GradleProcessorPath(val groupId: String, val artifactId: String) {
  companion object {
    fun of(dependencyNotation: String): GradleProcessorPath? {
      val parts = dependencyNotation.split(':')
      val groupId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
      val artifactId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
      return GradleProcessorPath(groupId, artifactId)
    }
  }
}

private val PROCESSOR_DEPENDENCY_REGEX = Regex(
  """(?m)^(\s*)(annotationProcessor|testAnnotationProcessor|implementation|testImplementation)\s*(?:\(\s*)?["']([^"']+)["']\s*\)?"""
)

private val KAPT_DEPENDENCY_REGEX = Regex("""(?m)^\s*(kapt|kaptTest)\s*(?:\(\s*)?["']([^"']+)["']""")

private val KNOWN_PROCESSOR_ARTIFACTS = setOf(
  GradleProcessorPath("org.mapstruct", "mapstruct-processor"),
  GradleProcessorPath("com.google.dagger", "dagger-compiler"),
  GradleProcessorPath("com.google.dagger", "hilt-compiler"),
  GradleProcessorPath("androidx.room", "room-compiler"),
  GradleProcessorPath("org.hibernate.orm", "hibernate-jpamodelgen"),
  GradleProcessorPath("org.hibernate", "hibernate-jpamodelgen"),
  GradleProcessorPath("io.micronaut", "micronaut-inject-java"),
  GradleProcessorPath("com.google.auto.service", "auto-service"),
  GradleProcessorPath("com.querydsl", "querydsl-apt"),
)

private val KNOWN_NON_LOMBOK_PROCESSOR_CLASSES = listOf(
  "org.mapstruct.ap.MappingProcessor",
  "dagger.internal.codegen.ComponentProcessor",
  "com.google.dagger.hilt.processor.internal.root.RootProcessor",
  "androidx.room.RoomProcessor",
  "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor",
  "io.micronaut.annotation.processing.TypeElementVisitorProcessor",
  "com.google.auto.service.processor.AutoServiceProcessor",
  "com.querydsl.apt.jpa.JPAAnnotationProcessor",
)
