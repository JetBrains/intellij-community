// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.compiler.CompilerConfiguration
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.configuration.AbstractKotlinCompilerProjectPostConfigurator
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KaptProcessorDependency
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.relativeTo

private const val KAPT_PLUGIN_ID = "kapt"
private const val KAPT_GRADLE_PLUGIN_NAME = "kotlin.kapt"
private const val KAPT_KEEP_JAVAC_PROCESSORS_OPTION = "keepJavacAnnotationProcessors = true"
private const val KSP_GRADLE_PLUGIN_ID = "com.google.devtools.ksp"
private val KSP_GRADLE_PLUGIN_ID_REGEX = Regex.escape(KSP_GRADLE_PLUGIN_ID)
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
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val document = psiDocumentManager.getDocument(this)

    val fileText = if (document != null) {
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
        document.text
    } else {
        text
    }

    val changes = findKaptDependencyChanges(fileText)
    if (changes.isEmpty) return

    val dependenciesManipulator = GradleBuildScriptSupport.getManipulator(this).kaptDependenciesManipulator ?: return

    changedFiles.storeOriginalFileContent(this)

    dependenciesManipulator.addDependencies(changes.dependenciesToAdd)
    dependenciesManipulator.removeDependencies(changes.dependenciesToRemove)
    dependenciesManipulator.reformat()
}

class KaptGradleProjectPostConfigurator : AbstractKotlinCompilerProjectPostConfigurator(KAPT_PLUGIN_ID) {
    override fun isApplicable(module: Module): Boolean =
        module.isGradleModule &&
                compilerPluginProjectConfigurators(module).isNotEmpty() &&
                !module.hasKaptGradlePluginConfigured() &&
                module.hasNonLombokAnnotationProcessor() &&
                !module.hasKspGradlePluginConfigured()
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

internal fun Module.hasKspGradlePluginConfigured(): Boolean {
    val buildScript = getBuildScriptPsiFile() ?: return false
    return KSP_GRADLE_PLUGIN_REGEX.containsMatchIn(buildScript.text) ||
            KSP_DEPENDENCY_REGEX.containsMatchIn(buildScript.text)
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
    val parentDirectory = (virtualFile ?: originalFile.virtualFile)?.parent ?: return
    val configFile = sourceModule.findLombokConfigFile(parentDirectory) ?: return
    val configFilePath = configFile.toNioPath()
    val parentPath = parentDirectory.toNioPath()
    val relativePath = configFilePath.relativeTo(parentPath)

    GradleBuildScriptSupport.getManipulator(this).configurePluginOptions(
        "kotlinLombok",
        changedFiles,
        "lombokConfigurationFile(file(\"$relativePath\"))",
    )
}

private fun Module.findLombokConfigFile(parentDirectory: VirtualFile): VirtualFile? {
    val processor = CommonProcessors.FindFirstProcessor<VirtualFile>()
    for (scope in arrayOf(GlobalSearchScopes.directoryScope(project, parentDirectory, true), project.projectScope())) {
        FilenameIndex.processFilesByNames(
            setOf("lombok.config"),
            false,
            scope,
            null,
            processor
        )
        processor.foundValue?.let { return it }
    }
    return null
}

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
        dependencyConfiguration = sourceConfiguration.dependencyConfiguration,
        kaptConfiguration = sourceConfiguration.kaptConfiguration,
        notation = notation,
        dropOriginal = sourceConfiguration.dropOriginal
    )
}

private fun kaptPluginExpression(forKotlinDsl: Boolean): String =
    if (forKotlinDsl) "kotlin(\"kapt\")" else "id \"org.jetbrains.kotlin.kapt\""

private data class KaptDependency(val configuration: String, val notation: String)

private val KaptProcessorDependency.kaptDependency: KaptDependency
    get() = KaptDependency(kaptConfiguration, notation)

private data class KaptDependencyChanges(
    val dependenciesToAdd: List<KaptProcessorDependency>,
    val dependenciesToRemove: List<KaptProcessorDependency>,
) {
    val isEmpty: Boolean
        get() = dependenciesToAdd.isEmpty() && dependenciesToRemove.isEmpty()
}

private fun findKaptDependencyChanges(fileText: String): KaptDependencyChanges {
    val processorDependencies = PROCESSOR_DEPENDENCY_REGEX
        .findAll(fileText)
        .mapNotNull(MatchResult::toKaptProcessorDependency)
        .toList()

    val existingKaptDependencies = KAPT_DEPENDENCY_REGEX
        .findAll(fileText)
        .map { match ->
            KaptDependency(
                configuration = match.groupValues[1],
                notation = match.groupValues[2],
            )
        }
        .toSet()

    val dependenciesToAdd = processorDependencies
        .distinctBy(KaptProcessorDependency::kaptDependency)
        .filterNot { it.kaptDependency in existingKaptDependencies }

    val dependenciesToRemove = processorDependencies
        .filter(KaptProcessorDependency::dropOriginal)

    return KaptDependencyChanges(
        dependenciesToAdd = dependenciesToAdd,
        dependenciesToRemove = dependenciesToRemove,
    )
}

private enum class GradleProcessorDependencyConfiguration(
    val dependencyConfiguration: String,
    val kaptConfiguration: String,
    val dropOriginal: Boolean = false,
    val acceptsAnyProcessor: Boolean,
) {
    ANNOTATION_PROCESSOR("annotationProcessor", "kapt", dropOriginal = true, acceptsAnyProcessor = true),
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

private val KSP_GRADLE_PLUGIN_REGEX = Regex(
    listOf(
        """id\s*\(\s*["']$KSP_GRADLE_PLUGIN_ID_REGEX["']\s*\)""",
        """id\s+["']$KSP_GRADLE_PLUGIN_ID_REGEX["']""",
        """apply\s*\(?\s*plugin\s*[:=]\s*["']$KSP_GRADLE_PLUGIN_ID_REGEX["']\s*\)?""",
        """alias\s*\([^)\n]*\bksp\b[^)\n]*\)""",
    ).joinToString(separator = "|", prefix = "(?m)(?:", postfix = ")")
)

private val KSP_DEPENDENCY_REGEX = Regex(
    listOf(
        """^\s*ksp\w*\s*(?:\(\s*)?["']""",
        """^\s*add\s*\(\s*["']ksp\w*["']\s*,""",
    ).joinToString(separator = "|", prefix = "(?m)(?:", postfix = ")")
)

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
