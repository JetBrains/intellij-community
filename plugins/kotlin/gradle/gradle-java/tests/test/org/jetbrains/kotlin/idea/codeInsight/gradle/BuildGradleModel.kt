// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import kotlinx.coroutines.runBlocking
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.plugins.gradle.model.ClassSetImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportAction
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.service.execution.createMainInitScript
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.tooling.builder.AbstractModelBuilderTest
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.test.fail

class BuiltGradleModel<T : Any>(val modules: Map<IdeaModule, T?>) {
    private val byGradleProjectPath = modules.mapKeys { (module, _) -> module.gradleProject.path }

    fun getByProjectPathOrThrow(projectPath: String): T? {
        if (projectPath !in byGradleProjectPath) fail("Missing project with path '$projectPath'. Found ${byGradleProjectPath.keys}")
        return byGradleProjectPath[projectPath]
    }

    fun getNotNullByProjectPathOrThrow(projectPath: String): T {
        return getByProjectPathOrThrow(projectPath) ?: fail(
            "Missing (null) value for project with path '$projectPath'\n" +
                    "Contains non-null values for ${byGradleProjectPath.filter { (_, value) -> value != null }.keys}"
        )
    }
}

data class BuildGradleModelDebuggerOptions(
    val suspend: Boolean = true,
    val port: Int = 5005
)

fun <T : Any> buildGradleModel(
    projectPath: File, gradleVersion: GradleVersion, javaHomePath: String, clazz: KClass<T>,
    debuggerOptions: BuildGradleModelDebuggerOptions? = null
): BuiltGradleModel<T> {
    val connector = GradleConnector.newConnector()
    connector.useDistribution(AbstractModelBuilderTest.DistributionLocator().getDistributionFor(gradleVersion))
    connector.forProjectDirectory(projectPath)

    (connector as DefaultGradleConnector).daemonMaxIdleTime(
        System.getProperty("gradleDaemonMaxIdleTime", "10").toIntOrNull() ?: 10, TimeUnit.SECONDS
    )

    connector.connect().use { gradleConnection ->
        val projectImportAction = ProjectImportAction(false, true)
        projectImportAction.addProjectImportModelProvider(
            ClassSetImportModelProvider(
                setOf(
                    clazz.java,
                    /* Representative of the `kotlin.project-module` module */
                    KotlinCompilation::class.java,

                    /* Representative of the `kotlin-tooling-core` library */
                    Extras::class.java,

                    /* Representative of the `kotlin-gradle-plugin-idea` library */
                    IdeaKotlinDependency::class.java,

                    /* Representative of the kotlin stdlib */
                    Unit::class.java
                ), setOf(IdeaProject::class.java)
            )
        )

        val executionSettings = GradleExecutionSettings(null, null, DistributionType.BUNDLED, false)
        GradleExecutionHelper.attachTargetPathMapperInitScript(executionSettings)
        val toolingExtensionClasses = AbstractModelBuilderTest.getToolingExtensionClasses()
        val kotlinToolingExtensionClasses = setOf(
            /* Representative of the `gradle-tooling` module */
            KotlinMPPGradleModelBuilder::class.java,

            /* Representative of the `kotlin.project-module` module */
            KotlinCompilation::class.java,

            /* Representative of the `kotlin-tooling-core` library */
            Extras::class.java,

            /* Representative of the `kotlin-gradle-plugin-idea` library */
            IdeaKotlinDependency::class.java,

            /* Representative of the kotlin stdlib */
            Unit::class.java
        )
        val initScript = createMainInitScript(false, toolingExtensionClasses + kotlinToolingExtensionClasses)
        executionSettings.withArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.absolutePath)

        val buildActionExecutor = gradleConnection.action(projectImportAction)
        buildActionExecutor.withArguments(executionSettings.arguments)

        buildActionExecutor.setJavaHome(File(javaHomePath))
        buildActionExecutor.setStandardOutput(System.out)
        buildActionExecutor.setStandardError(System.out)
        buildActionExecutor.setJvmArguments(listOfNotNull("-Xmx512m", debuggerOptions?.toJvmArgumentString()))


        val allModels = runBlocking {
            suspendCoroutine { continuation ->
                val buildActionResultHandler = object : ResultHandler<ProjectImportAction.AllModels> {
                    override fun onComplete(result: ProjectImportAction.AllModels) {
                        continuation.resume(result)
                    }

                    override fun onFailure(cause: GradleConnectionException) {
                        continuation.resumeWithException(cause)
                    }
                }

                buildActionExecutor.run(buildActionResultHandler)
            }
        }

        val ideaProject = allModels.getModel(IdeaProject::class.java) ?: fail("Missing '${IdeaProject::class.simpleName}' model")
        return BuiltGradleModel(ideaProject.modules.associateWith { module -> allModels.getModel(module, clazz.java) })
    }
}

private fun BuildGradleModelDebuggerOptions.toJvmArgumentString(): String {
    return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${if (suspend) "y" else "n"},address=${port}"
}

fun <T : Any, R : Any> BuiltGradleModel<T>.map(mapper: (T) -> R): BuiltGradleModel<R> {
    return BuiltGradleModel(modules.mapValues { (_, value) -> if (value == null) null else mapper(value) })
}