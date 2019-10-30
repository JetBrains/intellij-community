// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CACHE_MAPPER_BRANCHING
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CachedExtractedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCachingChain
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCachingManager.cacheCompilerArgument
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder.Companion.getTargets
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.KotlinTaskProperties
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.util.*

@Deprecated("Use org.jetbrains.kotlin.idea.projectModel.CachedArgsInfo instead")
interface ArgsInfo : Serializable {
    val currentArguments: List<String>
    val defaultArguments: List<String>
    val dependencyClasspath: List<String>
}

@Suppress("DEPRECATION")
@Deprecated("Use org.jetbrains.kotlin.idea.gradleTooling.CachedCompilerArgumentsBySourceSet instead")
typealias CompilerArgumentsBySourceSet = Map<String, ArgsInfo>
typealias CachedCompilerArgumentsBySourceSet = Map<String, CachedExtractedArgsInfo>

typealias AdditionalVisibleSourceSetsBySourceSet = Map</* Source Set Name */ String, /* Visible Source Set Names */ Set<String>>

interface KotlinGradleModel : Serializable {
    val hasKotlinPlugin: Boolean

    @Suppress("DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")
    @Deprecated(
        "Use 'cachedCompilerArgumentsBySourceSet' instead",
        ReplaceWith("cachedCompilerArgumentsBySourceSet"),
        DeprecationLevel.ERROR
    )
    val compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet
    val additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet
    val cachedCompilerArgumentsBySourceSet: CachedCompilerArgumentsBySourceSet
    val coroutines: String?
    val platformPluginId: String?
    val implements: List<String>
    val kotlinTarget: String?
    val kotlinTaskProperties: KotlinTaskPropertiesBySourceSet
    val gradleUserHome: String
    val partialCacheAware: CompilerArgumentsCacheAware
}

data class KotlinGradleModelImpl(
    override val hasKotlinPlugin: Boolean,
    @Suppress("OverridingDeprecatedMember", "DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")
    override val compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet,
    override val cachedCompilerArgumentsBySourceSet: CachedCompilerArgumentsBySourceSet,
    override val additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet,
    override val coroutines: String?,
    override val platformPluginId: String?,
    override val implements: List<String>,
    override val kotlinTarget: String? = null,
    override val kotlinTaskProperties: KotlinTaskPropertiesBySourceSet,
    override val gradleUserHome: String,
    override val partialCacheAware: CompilerArgumentsCacheAware
) : KotlinGradleModel

abstract class AbstractKotlinGradleModelBuilder : ModelBuilderService {
    companion object {
        val kotlinCompileJvmTaskClasses = listOf(
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompile_Decorated",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompileWithWorkers_Decorated"
        )

        val kotlinCompileTaskClasses = kotlinCompileJvmTaskClasses + listOf(
            "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile_Decorated",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon_Decorated",
            "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompileWithWorkers_Decorated",
            "org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommonWithWorkers_Decorated"
        )
        val platformPluginIds = listOf("kotlin-platform-jvm", "kotlin-platform-js", "kotlin-platform-common")
        val pluginToPlatform = linkedMapOf(
            "kotlin" to "kotlin-platform-jvm",
            "kotlin2js" to "kotlin-platform-js"
        )
        val kotlinPluginIds = listOf("kotlin", "kotlin2js", "kotlin-android")
        const val ABSTRACT_KOTLIN_COMPILE_CLASS = "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile"

        const val kotlinProjectExtensionClass = "org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension"
        const val kotlinSourceSetClass = "org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet"

        const val kotlinPluginWrapper = "org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt"

        private val propertyClassPresent = GradleVersion.current() >= GradleVersion.version("4.3")

        fun Task.getSourceSetName(): String = try {
            val method = javaClass.methods.firstOrNull { it.name.startsWith("getSourceSetName") && it.parameterTypes.isEmpty() }
            val sourceSetName = method?.invoke(this)
            when {
                sourceSetName is String -> sourceSetName
                propertyClassPresent && sourceSetName is Property<*> -> sourceSetName.get() as? String
                else -> null
            }
        } catch (e: InvocationTargetException) {
            null // can be thrown if property is not initialized yet
        } ?: "main"
    }
}

private const val REQUEST_FOR_NON_ANDROID_MODULES_ONLY = "*"

class AndroidAwareGradleModelProvider<TModel>(
    private val modelClass: Class<TModel>,
    private val androidPluginIsRequestingVariantSpecificModels: Boolean
) : ProjectImportModelProvider {
    override fun populateBuildModels(
        controller: BuildController,
        buildModel: GradleBuild,
        consumer: ProjectImportModelProvider.BuildModelConsumer
    ) = Unit

    override fun populateProjectModels(
        controller: BuildController,
        projectModel: Model,
        modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
    ) {
        val supportsParametrizedModels: Boolean = controller.findModel(BuildEnvironment::class.java)?.gradle?.gradleVersion?.let {
            // Parametrized build models were introduced in 4.4. Make sure that gradle import does not fail on pre-4.4
            GradleVersion.version(it) >= GradleVersion.version("4.4")
        } ?: false

        val model = if (androidPluginIsRequestingVariantSpecificModels && supportsParametrizedModels) {
            controller.findModel(projectModel, modelClass, ModelBuilderService.Parameter::class.java) {
                it.value = REQUEST_FOR_NON_ANDROID_MODULES_ONLY
            }
        } else {
            controller.findModel(projectModel, modelClass)
        }
        if (model != null) {
            modelConsumer.consume(model, modelClass)
        }
    }

    class Result(
        private val hasProjectAndroidBasePlugin: Boolean,
        private val requestedVariantNames: Set<String>?
    ) {
        fun shouldSkipBuildAllCall(): Boolean =
            hasProjectAndroidBasePlugin && requestedVariantNames?.singleOrNull() == REQUEST_FOR_NON_ANDROID_MODULES_ONLY

        fun shouldSkipSourceSet(sourceSetName: String): Boolean =
            requestedVariantNames != null && !requestedVariantNames.contains(sourceSetName.lowercase(Locale.getDefault()))
    }

    companion object {
        fun parseParameter(project: Project, parameterValue: String?): Result {
            return Result(
                hasProjectAndroidBasePlugin = project.plugins.findPlugin("com.android.base") != null,
                requestedVariantNames = parameterValue?.splitToSequence(',')?.map { it.lowercase(Locale.getDefault()) }?.toSet()
            )
        }
    }
}

class KotlinGradleModelBuilder : AbstractKotlinGradleModelBuilder(), ModelBuilderService.Ex {
    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder.create(project, e, "Gradle import errors")
            .withDescription("Unable to build Kotlin project configuration")
    }

    override fun canBuild(modelName: String?): Boolean = modelName == KotlinGradleModel::class.java.name

    private fun getImplementedProjects(project: Project): List<Project> {
        return listOf("expectedBy", "implement")
            .flatMap { project.configurations.findByName(it)?.dependencies ?: emptySet<Dependency>() }
            .filterIsInstance<ProjectDependency>()
            .mapNotNull { it.dependencyProject }
    }

    // see GradleProjectResolverUtil.getModuleId() in IDEA codebase
    private fun Project.pathOrName() = if (path == ":") name else path

    private fun Task.getDependencyClasspath(): List<String> {
        try {
            val abstractKotlinCompileClass = javaClass.classLoader.loadClass(ABSTRACT_KOTLIN_COMPILE_CLASS)
            val getCompileClasspath = abstractKotlinCompileClass.getDeclaredMethod("getCompileClasspath").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            return (getCompileClasspath.invoke(this) as Collection<File>).map { it.path }
        } catch (e: ClassNotFoundException) {
            // Leave arguments unchanged
        } catch (e: NoSuchMethodException) {
            // Leave arguments unchanged
        } catch (e: InvocationTargetException) {
            // We can safely ignore this exception here as getCompileClasspath() gets called again at a later time
            // Leave arguments unchanged
        }
        return emptyList()
    }

    private fun getCoroutines(project: Project): String? {
        val kotlinExtension = project.extensions.findByName("kotlin") ?: return null
        val experimentalExtension = try {
            kotlinExtension::class.java.getMethod("getExperimental").invoke(kotlinExtension)
        } catch (e: NoSuchMethodException) {
            return null
        }

        return try {
            experimentalExtension::class.java.getMethod("getCoroutines").invoke(experimentalExtension)?.toString()
        } catch (e: NoSuchMethodException) {
            null
        }
    }

    override fun buildAll(modelName: String, project: Project): KotlinGradleModelImpl? {
        return buildAll(project, null)
    }

    override fun buildAll(modelName: String, project: Project, builderContext: ModelBuilderContext): KotlinGradleModelImpl? {
        return buildAll(project, builderContext)
    }

    private fun buildAll(project: Project, builderContext: ModelBuilderContext?): KotlinGradleModelImpl? {
        // When running in Android Studio, Android Studio would request specific source sets only to avoid syncing
        // currently not active build variants. We convert names to the lower case to avoid ambiguity with build variants
        // accidentally named starting with upper case.
        val androidVariantRequest = AndroidAwareGradleModelProvider.parseParameter(project, builderContext?.parameter)
        if (androidVariantRequest.shouldSkipBuildAllCall()) return null
        val kotlinPluginId = kotlinPluginIds.singleOrNull { project.plugins.findPlugin(it) != null }
        val platformPluginId = platformPluginIds.singleOrNull { project.plugins.findPlugin(it) != null }
        val targets = project.getTargets(includeSinglePlatform = true)

        if (kotlinPluginId == null && platformPluginId == null && targets == null) {
            return null
        }

        val cachedCompilerArgumentsBySourceSet = LinkedHashMap<String, CachedExtractedArgsInfo>()
        val additionalVisibleSourceSets = LinkedHashMap<String, Set<String>>()
        val extraProperties = HashMap<String, KotlinTaskProperties>()
        val masterMapper = builderContext?.getData(CACHE_MAPPER_BRANCHING) ?: return null
        val detachableMapper = masterMapper.branchOffDetachable(project.name == "buildSrc")

        val kotlinCompileTasks = if (targets != null) {
            targets.flatMap { target -> KotlinMPPGradleModelBuilder.getCompilations(target) ?: emptyList() }
                .mapNotNull { compilation -> KotlinMPPGradleModelBuilder.getCompileKotlinTaskName(project, compilation) }
        } else {
            project.getAllTasks(false)[project]?.filter { it.javaClass.name in kotlinCompileTaskClasses } ?: emptyList()
        }

        kotlinCompileTasks.forEach { compileTask ->
            if (compileTask.javaClass.name !in kotlinCompileTaskClasses) return@forEach
            val sourceSetName = compileTask.getSourceSetName()
            if (androidVariantRequest.shouldSkipSourceSet(sourceSetName)) return@forEach
            val currentArguments = CompilerArgumentsCachingChain.extractAndCacheTask(compileTask, detachableMapper, defaultsOnly = false)
            val defaultArguments = CompilerArgumentsCachingChain.extractAndCacheTask(compileTask, detachableMapper, defaultsOnly = true)
            val dependencyClasspath = compileTask.getDependencyClasspath().map { cacheCompilerArgument(it, detachableMapper) }
            cachedCompilerArgumentsBySourceSet[sourceSetName] =
                CachedExtractedArgsInfo(detachableMapper.cacheOriginIdentifier, currentArguments, defaultArguments, dependencyClasspath)

            additionalVisibleSourceSets[sourceSetName] = getAdditionalVisibleSourceSets(project, sourceSetName)
            extraProperties.acknowledgeTask(compileTask, null)
        }

        val platform = platformPluginId ?: pluginToPlatform.entries.singleOrNull { project.plugins.findPlugin(it.key) != null }?.value
        val implementedProjects = getImplementedProjects(project)

        return KotlinGradleModelImpl(
            hasKotlinPlugin = kotlinPluginId != null || platformPluginId != null,
            compilerArgumentsBySourceSet = emptyMap(),
            cachedCompilerArgumentsBySourceSet = cachedCompilerArgumentsBySourceSet,
            additionalVisibleSourceSets = additionalVisibleSourceSets,
            coroutines = getCoroutines(project),
            platformPluginId = platform,
            implements = implementedProjects.map { it.pathOrName() },
            kotlinTarget = platform ?: kotlinPluginId,
            kotlinTaskProperties = extraProperties,
            gradleUserHome = project.gradle.gradleUserHomeDir.absolutePath,
            partialCacheAware = detachableMapper.detachCacheAware()
        )
    }
}
