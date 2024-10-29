// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations

import com.intellij.application.subscribe
import com.intellij.execution.*
import com.intellij.execution.actions.ConfigurationContext.createEmptyContextForLocation
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.*
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.findMostSpecificExistingFileOrNewDefault
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.codeInsight.gradle.combineMultipleFailures
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.name.FqName
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.fail
import kotlin.time.Duration

/**
 * Executes a given run configuration associated with the configured functions (see [ExecuteRunConfigurationsConfiguration.functionFqNames])
 * The output of the execution will be put into a test-data file.
 */
object ExecuteRunConfigurationsChecker : AbstractTestChecker<ExecuteRunConfigurationsConfiguration>() {

    override fun createDefaultConfiguration(): ExecuteRunConfigurationsConfiguration {
        return ExecuteRunConfigurationsConfiguration()
    }

    override fun KotlinMppTestsContext.check() {
        testConfiguration.getConfiguration(ExecuteRunConfigurationsChecker)
            .functionFqNames.combineMultipleFailures { functionFqn -> checkFunction(functionFqn) }
    }

    private fun KotlinMppTestsContext.checkFunction(fqn: String) {
        val actualOutput = execute(assertRunConfiguration(fqn)).joinToString("\n")
        val expectedTestDataFile = findMostSpecificExistingFileOrNewDefault(checkerClassifier = "run-output-$fqn")

        KotlinTestUtils.assertEqualsToFile(expectedTestDataFile, actualOutput) { text ->
            text.replace(Regex("""\d\d?:\d\d?:\d\d?\s*(\w\w)?:"""), "<time>:")
                .replace(Regex("""\h*Download.*\n"""), "")
                .lineSequence()
                .map { it.split("//").first().trimEnd() } // Support comments
                .filterNot { it.startsWith("Starting Gradle Daemon") }
                .filterNot { it.startsWith("Gradle Daemon started") }
                .joinToString(System.lineSeparator())
        }
    }

    /**
     * Executes the given [runConfiguration] and returns the output
     */
    private fun KotlinMppTestsContext.execute(runConfiguration: RunnerAndConfigurationSettings): List<String> =
        runBlockingWithTimeout(testConfiguration.getConfiguration(ExecuteRunConfigurationsChecker).executionTimeout) { continuation ->
            val output = mutableListOf<String>()
            val disposable = Disposer.newDisposable()
            coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }


            /*
            Creates the process listener which is collecting the execution output (requires an ExternalSystemProcessHandler).
            This listener will also be able to determine when the execution is over (using the processTerminated callback)
             */
            fun processListener(processHandler: ExternalSystemProcessHandler) = object : ProcessListener {
                /**
                 * Quirk in [com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask.execute]:
                 * In case of a build failure, the 'onFailure' call will terminate the process and invoke
                 * the 'processTerminated' listeners first.
                 *
                 * However, in the 'onEnd' callback will be invoked afterward with the 'farewell message'
                 * (see [ExternalSystemRunnableState])
                 *
                 * On the happy path (no failure), this farewell message will be sent first and
                 * will be available in 'onTextAvailable' before the 'processTerminated' is called.
                 */
                val farewellMessageReceived = Job()

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    launch {
                        output.add(event.text.trim())
                        if (event.processHandler.isProcessTerminated) {
                            farewellMessageReceived.complete()
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    launch {
                        if (event.exitCode != 0) {
                            farewellMessageReceived.join()

                            val task = processHandler.task ?: fail("Missing task")

                            /* Await the task getting updated */
                            while (task.state == ExternalSystemTaskState.IN_PROGRESS) {
                                yield()
                            }

                            task.error?.stackTraceToString()?.let { stackTraceString ->
                                output += "\n"
                                output += stackTraceString.lineSequence()
                                    .filterNot { it.isBlank() }
                                    .take(2)
                            }
                        }
                        output.add("\n<exitCode: ${event.exitCode}>")
                        continuation.resume(output.toList())
                    }
                }
            }

            /*
            Special extension just living for this execution test:
            We will ensure that we can set our process listener early enough!
             */
            val runConfigurationExtension = object : RunConfigurationExtension() {
                override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

                override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
                    configuration: T & Any, params: JavaParameters, runnerSettings: RunnerSettings?
                ) = Unit

                override fun attachToProcess(
                    configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?
                ) {
                    handler.addProcessListener(processListener(handler as ExternalSystemProcessHandler))
                }
            }

            RunConfigurationExtension.EP_NAME.point.registerExtension(
                runConfigurationExtension, LoadingOrder.ANY, disposable
            )

            /* the 'runBlockingWithTimeout' block will wait for this callbacks */
            ExecutionManager.EXECUTION_TOPIC.subscribe(disposable, object : ExecutionListener {
                override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) =
                    continuation.resumeWithException(cause ?: error("Process not started"))
            })


            /* Finally trigger execution of the run configuration */
            val environment = ExecutionEnvironmentBuilder
                .create(DefaultRunExecutor.getRunExecutorInstance(), runConfiguration)
                .build()

            ExecutionManager.getInstance(testProject).restartRunProfile(environment)
        }

    private fun <T> runBlockingWithTimeout(timeout: Duration, action: CoroutineScope.(continuation: Continuation<T>) -> Unit): T {
        return runBlocking {
            withTimeout(timeout) {
                suspendCoroutine { continuation -> action(continuation) }
            }
        }
    }

    private fun KotlinMppTestsContext.assertRunConfiguration(functionFqn: String): RunnerAndConfigurationSettings {
        return findRunConfiguration(functionFqn) ?: fail("Missing runConfiguration for '$functionFqn'")
    }

    private fun KotlinMppTestsContext.findRunConfiguration(functionFqn: String): RunnerAndConfigurationSettings? {
        ThreadingAssertions.assertBackgroundThread()
        return runBlocking {
            smartReadAction(testProject) {
                val psiElement = findTestPsiElementByFqn(functionFqn)
                createEmptyContextForLocation(PsiLocation(psiElement)).configuration
            }
        }
    }

    /**
     * Finds either the class (by fqn) or test function (by fqn) to execute the test
     */
    private fun KotlinMppTestsContext.findTestPsiElementByFqn(fqn: String): PsiElement = runReadAction {
        val fqName = FqName(fqn)
        KotlinFullClassNameIndex[fqName.asString(), testProject, testProject.allScope()].apply {
            if (size == 1) return@runReadAction single()
        }

        KotlinFunctionShortNameIndex[fqName.shortName().asString(), testProject, testProject.allScope()]
            .filter { it.fqName == fqName }
            .apply {
                if (isEmpty()) fail("Missing function '$fqn'")
                if (size > 1) fail("Multiple functions with fqn '$fqn'")
            }
            .single()
            .identifyingElement ?: fail("Missing 'identifyingElement'")
    }
}


