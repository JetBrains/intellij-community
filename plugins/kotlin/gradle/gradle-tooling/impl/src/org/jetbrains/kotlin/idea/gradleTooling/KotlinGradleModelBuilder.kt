// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy
import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicyCache
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencyResolver
import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.idea.gradleTooling.AndroidAwareGradleModelProvider.Companion.isAgpApplied
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinTaskProperties
import org.jetbrains.kotlin.tooling.core.Interner
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.util.*

typealias AdditionalVisibleSourceSetsBySourceSet = Map</* Source Set Name */ String, /* Visible Source Set Names */ Set<String>>

typealias CompilerArgumentsBySourceSet = Map< /* Source Set Name */ String, /* Arguments */ List<String>>

interface KotlinGradleModel : Serializable {
    val hasKotlinPlugin: Boolean
    val compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet
    val additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet
    val coroutines: String?
    val platformPluginId: String?
    val implements: List<String>
    val kotlinTarget: String?
    val kotlinTaskProperties: KotlinTaskPropertiesBySourceSet
    val gradleUserHome: String
    val kotlinGradlePluginVersion: KotlinGradlePluginVersion?
}

data class KotlinGradleModelImpl(
    override val hasKotlinPlugin: Boolean,
    override val compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet,
    override val additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet,
    override val coroutines: String?,
    override val platformPluginId: String?,
    override val implements: List<String>,
    override val kotlinTarget: String? = null,
    override val kotlinTaskProperties: KotlinTaskPropertiesBySourceSet,
    override val gradleUserHome: String,
    override val kotlinGradlePluginVersion: KotlinGradlePluginVersion?
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

        fun Task.getSourceSetName(): String = try {
            val method = javaClass.methods.firstOrNull { it.name.startsWith("getSourceSetName") && it.parameterTypes.isEmpty() }
            when (val sourceSetName = method?.invoke(this)) {
                is String -> sourceSetName
                is Property<*> -> sourceSetName.get() as? String
                else -> null
            }
        } catch (e: InvocationTargetException) {
            null // can be thrown if property is not initialized yet
        } ?: "main"
    }
}

private const val REQUEST_FOR_NON_ANDROID_MODULES_ONLY = "*"

class AndroidAwareGradleModelProvider(
    private val androidPluginIsRequestingVariantSpecificModels: Boolean
) : ProjectImportModelProvider {
    private val modelClass = KotlinGradleModel::class.java
    override fun populateModels(controller: BuildController, buildModels: Collection<GradleBuild>, modelConsumer: GradleModelConsumer) {
        if (androidPluginIsRequestingVariantSpecificModels) {
            GradleModelProviderUtil.buildModelsWithParameter(
                controller,
                buildModels,
                modelClass,
                modelConsumer,
                ModelBuilderService.Parameter::class.java
            ) {
                it.value = REQUEST_FOR_NON_ANDROID_MODULES_ONLY
            }
        } else {
            GradleModelProviderUtil.buildModels(controller, buildModels, modelClass, modelConsumer)
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
                hasProjectAndroidBasePlugin = project.isAgpApplied(),
                requestedVariantNames = parameterValue?.splitToSequence(',')?.map { it.lowercase(Locale.getDefault()) }?.toSet()
            )
        }

        fun Project.isAgpApplied(): Boolean = plugins.hasPlugin("com.android.base")
    }
}

class KotlinGradleModelBuilder : AbstractKotlinGradleModelBuilder(), ModelBuilderService.ParameterizedModelBuilderService {

    override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
        context.messageReporter.createMessage()
            .withGroup(this)
            .withKind(Message.Kind.WARNING)
            .withTitle("Gradle import errors")
            .withText("Unable to build Kotlin project configuration")
            .withException(exception)
            .reportMessage(project)
    }

    override fun canBuild(modelName: String?): Boolean = modelName == KotlinGradleModel::class.java.name

    private fun getImplementedProjectNames(project: Project): List<String> {
        return listOf("expectedBy", "implement")
            .flatMap { project.configurations.findByName(it)?.dependencies ?: emptySet<Dependency>() }
            .filterIsInstance<ProjectDependency>()
            .mapNotNull { dependency ->
                if (GradleVersionUtil.isCurrentGradleOlderThan("9.0")) {
                    val dependencyProject = GradleReflectionUtil.getValue(dependency, "getDependencyProject", Project::class.java)
                    dependencyProject?.pathOrName()
                } else {
                    dependency.pathOrName()
                }
            }
    }

    // see GradleProjectResolverUtil.getModuleId() in IDEA codebase
    private fun Project.pathOrName() = if (path == ":") name else path

    private fun ProjectDependency.pathOrName() = if (path == ":") name else path

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
        return buildAll(project, builderContext = null, parameter = null)
    }

    override fun buildAll(
        modelName: String,
        project: Project,
        builderContext: ModelBuilderContext,
        parameter: ModelBuilderService.Parameter?
    ): KotlinGradleModelImpl? {
        return buildAll(project, builderContext, parameter)
    }

    private fun buildAll(
        project: Project,
        builderContext: ModelBuilderContext?,
        parameter: ModelBuilderService.Parameter?
    ): KotlinGradleModelImpl? {
        val interner = Interner()
        // When running in Android Studio, Android Studio would request specific source sets only to avoid syncing
        // currently not active build variants. We convert names to the lower case to avoid ambiguity with build variants
        // accidentally named starting with upper case.
        val androidVariantRequest = AndroidAwareGradleModelProvider.parseParameter(project, parameter?.value)
        if (androidVariantRequest.shouldSkipBuildAllCall()) return null
        val kotlinPluginId = kotlinPluginIds.singleOrNull { project.plugins.findPlugin(it) != null }
            ?: project.getAgpBuildInKotlinPluginId()
        val platformPluginId = platformPluginIds.singleOrNull { project.plugins.findPlugin(it) != null }
        val target = project.getTarget()

        if (kotlinPluginId == null && platformPluginId == null && target == null) {
            return null
        }

        val compilerArgumentsBySourceSet = LinkedHashMap<String, List<String>>()
        val additionalVisibleSourceSets = LinkedHashMap<String, Set<String>>()
        val extraProperties = HashMap<String, KotlinTaskProperties>()

        val kotlinCompileTasks = target?.let { it.compilations ?: emptyList() }
            ?.mapNotNull { compilation -> compilation.getCompileKotlinTaskName(project) }
            ?: (project.getAllTasks(false)[project]?.filter { it.javaClass.name in kotlinCompileTaskClasses } ?: emptyList())

        kotlinCompileTasks.forEach { compileTask ->
            if (compileTask.javaClass.name !in kotlinCompileTaskClasses) return@forEach
            val sourceSetName = compileTask.getSourceSetName()
            if (androidVariantRequest.shouldSkipSourceSet(sourceSetName)) return@forEach
            compilerArgumentsBySourceSet[sourceSetName] = resolveCompilerArguments(compileTask).orEmpty()
                .map(interner::getOrPut)

            additionalVisibleSourceSets[sourceSetName] = getAdditionalVisibleSourceSets(project, sourceSetName)
            extraProperties.acknowledgeTask(compileTask, null)
        }

        val platform = platformPluginId ?: pluginToPlatform.entries.singleOrNull { project.plugins.findPlugin(it.key) != null }?.value

        if (builderContext != null) {
            downloadKotlinStdlibSourcesIfNeeded(project, builderContext)
        }

        return KotlinGradleModelImpl(
            hasKotlinPlugin = kotlinPluginId != null || platformPluginId != null,
            compilerArgumentsBySourceSet = compilerArgumentsBySourceSet,
            additionalVisibleSourceSets = additionalVisibleSourceSets,
            coroutines = getCoroutines(project),
            platformPluginId = platform,
            implements = getImplementedProjectNames(project),
            kotlinTarget = platform ?: kotlinPluginId,
            kotlinTaskProperties = extraProperties,
            gradleUserHome = project.gradle.gradleUserHomeDir.absolutePath,
            kotlinGradlePluginVersion = project.kotlinGradlePluginVersion()
        )
    }

    // Since AGP 8.12.0-alpha02 Kotlin support was introduced without the need to apply the "kotlin-android" plugin.
    // But AGP still applies 'KotlinBaseApiPlugin', so we are trying to detect it here by using the interface name from KGP-API
    // which 'KotlinBaseApiPlugin' implements.
    private fun Project.getAgpBuildInKotlinPluginId(): String? = if (isAgpApplied() &&
        plugins
            .matching { plugin ->
                plugin::class.java.superclass.interfaces.any {
                    it.name == "org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory"
                }
            }.isNotEmpty()
    ) {
        "kotlin-android"
    } else {
        null
    }

    private fun downloadKotlinStdlibSourcesIfNeeded(project: Project, context: ModelBuilderContext) {
        // If `idea.gradle.download.sources.force` is `true`, then sources will be downloaded anyway (so no need to do it again here)
        // if it is `false`, then we have to skip any source downloading here
        // So the only one valid case is if the force flag is absent
        if (System.getProperty("idea.gradle.download.sources.force") != null) return

        // Dependency download policy covers all other cases to determine whether sources are marked for download or not
        val dependencyDownloadPolicy = GradleDependencyDownloadPolicyCache.getInstance(context)
            .getDependencyDownloadPolicy(project)
        if (dependencyDownloadPolicy.isDownloadSources()) return

        if (GradleVersionUtil.isCurrentGradleAtLeast("7.3")) {
            downloadKotlinStdlibSources(project, context)
        } else {
            downloadKotlinStdlibSourcesDeprecated(project, context)
        }
    }

    private fun downloadKotlinStdlibSourcesDeprecated(project: Project, context: ModelBuilderContext) {
        val kotlinStdlib = project.configurations.detachedConfiguration()
        project.configurations.forEachUsedKotlinLibrary {
            kotlinStdlib.dependencies.add(it)
        }
        if (kotlinStdlib.dependencies.isEmpty()) {
            return
        }
        GradleDependencyResolver(context, project, GradleDependencyDownloadPolicy.SOURCES)
            .resolveDependencies(kotlinStdlib)
    }

    private fun Dependency.isPartOfKotlinStdlib() = "org.jetbrains.kotlin" == group || "org.jetbrains.kotlinx" == group

    private fun ConfigurationContainer.forEachUsedKotlinLibrary(dependencyConsumer: (Dependency) -> Unit) {
        for (configuration in this) {
            for (dependency in configuration.dependencies) {
                if (dependency.isPartOfKotlinStdlib()) {
                    dependencyConsumer.invoke(dependency)
                }
            }
        }
    }

    private fun downloadKotlinStdlibSources(project: Project, context: ModelBuilderContext) {
        JavaPluginUtil.getSourceSetContainer(project)?.forEach {
            val compileClasspath = (it.compileClasspath as? Configuration)
            if (compileClasspath != null && compileClasspath.isCanBeResolved) {
                downloadSourcesForCompileClasspathKoltinSdlibDependencies(context, project, compileClasspath)
            }
        }
    }

    private fun downloadSourcesForCompileClasspathKoltinSdlibDependencies(
        context: ModelBuilderContext,
        project: Project,
        compileClassPathConfiguration: Configuration?
    ) {
        val stdlibDependencyGroups =
            setOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx")
        GradleDependencyResolver(context, project, GradleDependencyDownloadPolicy.SOURCES)
            .resolveDependencies(compileClassPathConfiguration, stdlibDependencyGroups)
    }

    private fun ConfigurationContainer.forEachUsedDependency(
        configurationToExclude: Configuration,
        dependencyConsumer: (Dependency) -> Unit
    ) {
        for (configuration in this) {
            if (configuration == configurationToExclude) continue
            for (dependency in configuration.dependencies) {
                dependencyConsumer.invoke(dependency)
            }
        }
    }

    private fun Project.kotlinGradlePluginVersion(): KotlinGradlePluginVersion? {
        val extension = project.extensions.findByName("kotlin") ?: return null
        val kotlinGradlePluginVersionString = KotlinExtensionReflection(project, extension).kotlinGradlePluginVersion ?: return null
        return KotlinGradlePluginVersion.parse(kotlinGradlePluginVersionString)
    }
}
