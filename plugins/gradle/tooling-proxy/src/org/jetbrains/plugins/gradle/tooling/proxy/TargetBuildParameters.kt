// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.tooling.BuildAction
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.consumer.PhasedBuildAction.BuildActionWrapper
import java.io.Serializable

sealed class TargetBuildParameters : Serializable {

  abstract val gradleHome: String?
  abstract val gradleUserHome: String?
  abstract val arguments: List<String>
  abstract val jvmArguments: List<String>
  abstract val environmentVariables: Map<String, String>
  abstract val progressListenerOperationTypes: Set<OperationType>
  abstract val initScripts: Map<String, String>

  abstract class Builder<This : Builder<This>> {

    private var initScriptNameDedupSuffix = 0

    protected val arguments = mutableListOf<String>()
    protected val jvmArguments = mutableListOf<String>()
    protected val environmentVariables = mutableMapOf<String, String>()
    protected val progressListenerOperationTypes = mutableSetOf<OperationType>()
    protected var gradleHome: String? = null
    protected var gradleUserHome: String? = null
    protected val initScripts = mutableMapOf<String, String>()

    abstract fun getThis(): This

    fun useInstallation(gradleHome: String?): This {
      this.gradleHome = gradleHome
      return getThis()
    }

    fun useGradleUserHome(gradleUserHome: String?): This {
      this.gradleUserHome = gradleUserHome
      return getThis()
    }

    fun withArguments(vararg args: String): This {
      return withArguments(args.asIterable())
    }

    fun withArguments(args: Iterable<String>): This {
      arguments.addAll(args)
      return getThis()
    }

    fun withJvmArguments(vararg args: String): This {
      return withJvmArguments(args.asIterable())
    }

    fun withJvmArguments(args: Iterable<String>): This {
      jvmArguments.addAll(args)
      return getThis()
    }

    fun withEnvironmentVariables(envVars: Map<String, String>): This {
      environmentVariables.putAll(envVars)
      return getThis()
    }

    fun withSubscriptions(args: Iterable<OperationType>): This {
      progressListenerOperationTypes.addAll(args)
      return getThis()
    }

    fun withInitScript(filePrefix: String, initScript: String): This {
      val prefix = if (initScripts.containsKey(filePrefix)) "$filePrefix~${++initScriptNameDedupSuffix}" else filePrefix
      initScripts[prefix] = initScript
      return getThis()
    }

    abstract fun build(): TargetBuildParameters

    open fun buildIntermediateResultHandler(): TargetIntermediateResultHandler {
      return TargetIntermediateResultHandler.NOOP
    }
  }

  abstract class TaskAwareBuilder<This : TaskAwareBuilder<This>> : Builder<This>() {

    protected val tasks = ArrayList<String>()

    fun withTasks(vararg args: String): This {
      return withTasks(args.asIterable())
    }

    fun withTasks(args: Iterable<String>): This {
      tasks.addAll(args)
      return getThis()
    }
  }

  class BuildLauncherParametersBuilder : TaskAwareBuilder<BuildLauncherParametersBuilder>() {

    override fun getThis(): BuildLauncherParametersBuilder = this

    override fun build(): BuildLauncherParameters {
      return BuildLauncherParameters(tasks, gradleHome, gradleUserHome,
                                     arguments, jvmArguments, environmentVariables, progressListenerOperationTypes, initScripts)
    }
  }

  class TestLauncherParametersBuilder : Builder<TestLauncherParametersBuilder>() {

    override fun getThis(): TestLauncherParametersBuilder = this

    override fun build(): TestLauncherParameters {
      return TestLauncherParameters(gradleHome, gradleUserHome, arguments, jvmArguments, environmentVariables,
                                    progressListenerOperationTypes, initScripts)
    }
  }

  class ModelBuilderParametersBuilder<T>(
    private val modelType: Class<T>
  ) : TaskAwareBuilder<ModelBuilderParametersBuilder<T>>() {

    override fun getThis(): ModelBuilderParametersBuilder<T> = this

    override fun build(): ModelBuilderParameters<T> {
      return ModelBuilderParameters(modelType, tasks, gradleHome, gradleUserHome,
                                    arguments, jvmArguments, environmentVariables, progressListenerOperationTypes, initScripts)
    }
  }

  abstract class StreamedValueAwareBuilder<This : StreamedValueAwareBuilder<This>> : TaskAwareBuilder<This>() {

    private var streamedValueListener: StreamedValueListener? = null

    fun setStreamedValueListener(streamedValueListener: StreamedValueListener) {
      this.streamedValueListener = streamedValueListener
    }

    override fun buildIntermediateResultHandler(): TargetIntermediateResultHandler {
      return super.buildIntermediateResultHandler()
        .then(StreamedIntermediateResultHandler(streamedValueListener))
    }

    private class StreamedIntermediateResultHandler(
      private val streamedValueListener: StreamedValueListener?
    ) : TargetIntermediateResultHandler {

      override fun onResult(type: IntermediateResultType, result: Any?) {
        if (type == IntermediateResultType.STREAMED_VALUE) {
          streamedValueListener!!.onValue(result!!)
        }
      }
    }
  }

  class BuildActionParametersBuilder<T>(
    private val action: BuildAction<T>
  ) : StreamedValueAwareBuilder<BuildActionParametersBuilder<T>>() {

    override fun getThis(): BuildActionParametersBuilder<T> = this

    override fun build(): BuildActionParameters<T> {
      return BuildActionParameters(
        action, tasks, gradleHome, gradleUserHome,
        arguments, jvmArguments, environmentVariables, progressListenerOperationTypes, initScripts
      )
    }
  }

  class PhasedBuildActionParametersBuilder(
    private val projectsLoadedAction: BuildActionWrapper<Any>?,
    private val buildFinishedAction: BuildActionWrapper<Any>?
  ) : StreamedValueAwareBuilder<PhasedBuildActionParametersBuilder>() {

    override fun getThis(): PhasedBuildActionParametersBuilder = this

    override fun buildIntermediateResultHandler(): TargetIntermediateResultHandler {
      return super.buildIntermediateResultHandler()
        .then(PhasedIntermediateResultHandler(projectsLoadedAction, buildFinishedAction))
    }

    override fun build(): PhasedBuildActionParameters {
      return PhasedBuildActionParameters(
        projectsLoadedAction?.action, buildFinishedAction?.action, tasks, gradleHome, gradleUserHome,
        arguments, jvmArguments, environmentVariables, progressListenerOperationTypes, initScripts
      )
    }

    private class PhasedIntermediateResultHandler(
      private val projectsLoadedAction: BuildActionWrapper<Any>?,
      private val buildFinishedAction: BuildActionWrapper<Any>?
    ) : TargetIntermediateResultHandler {

      override fun onResult(type: IntermediateResultType, result: Any?) {
        if (type == IntermediateResultType.PROJECT_LOADED) {
          projectsLoadedAction!!.handler.onComplete(result!!)
        }
        if (type == IntermediateResultType.BUILD_FINISHED) {
          buildFinishedAction!!.handler.onComplete(result!!)
        }
      }
    }
  }
}

data class BuildLauncherParameters(val tasks: List<String>,
                                   override val gradleHome: String?,
                                   override val gradleUserHome: String?,
                                   override val arguments: List<String>,
                                   override val jvmArguments: List<String>,
                                   override val environmentVariables: Map<String, String>,
                                   override val progressListenerOperationTypes: Set<OperationType>,
                                   override val initScripts: Map<String, String>) : TargetBuildParameters()

data class TestLauncherParameters(override val gradleHome: String?,
                                  override val gradleUserHome: String?,
                                  override val arguments: List<String>,
                                  override val jvmArguments: List<String>,
                                  override val environmentVariables: Map<String, String>,
                                  override val progressListenerOperationTypes: Set<OperationType>,
                                  override val initScripts: Map<String, String>) : TargetBuildParameters()

data class ModelBuilderParameters<T>(val modelType: Class<T>,
                                     val tasks: List<String>,
                                     override val gradleHome: String?,
                                     override val gradleUserHome: String?,
                                     override val arguments: List<String>,
                                     override val jvmArguments: List<String>,
                                     override val environmentVariables: Map<String, String>,
                                     override val progressListenerOperationTypes: Set<OperationType>,
                                     override val initScripts: Map<String, String>) : TargetBuildParameters()

data class BuildActionParameters<T>(val buildAction: BuildAction<T>,
                                    val tasks: List<String>,
                                    override val gradleHome: String?,
                                    override val gradleUserHome: String?,
                                    override val arguments: List<String>,
                                    override val jvmArguments: List<String>,
                                    override val environmentVariables: Map<String, String>,
                                    override val progressListenerOperationTypes: Set<OperationType>,
                                    override val initScripts: Map<String, String>) : TargetBuildParameters()

data class PhasedBuildActionParameters(val projectsLoadedAction: BuildAction<Any>?,
                                       val buildFinishedAction: BuildAction<Any>?,
                                       val tasks: List<String>,
                                       override val gradleHome: String?,
                                       override val gradleUserHome: String?,
                                       override val arguments: List<String>,
                                       override val jvmArguments: List<String>,
                                       override val environmentVariables: Map<String, String>,
                                       override val progressListenerOperationTypes: Set<OperationType>,
                                       override val initScripts: Map<String, String>) : TargetBuildParameters()