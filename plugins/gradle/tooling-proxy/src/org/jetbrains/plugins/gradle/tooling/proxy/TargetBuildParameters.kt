// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.tooling.BuildAction
import java.io.Serializable

sealed class TargetBuildParameters : Serializable {
  abstract val arguments: List<String>
  abstract val jvmArguments: List<String>

  abstract class Builder {
    protected val arguments = mutableListOf<String>()
    protected val jvmArguments = mutableListOf<String>()
    fun withArguments(vararg args: String): Builder = apply { arguments.addAll(args) }
    fun withArguments(args: Iterable<String>) = apply { arguments.addAll(args) }
    fun withJvmArguments(vararg args: String): Builder = apply { jvmArguments.addAll(args) }
    fun withJvmArguments(args: Iterable<String>) = apply { jvmArguments.addAll(args) }
    abstract fun build(): TargetBuildParameters
  }

  interface TasksAwareBuilder {
    fun withTasks(vararg args: String): Builder
    fun withTasks(args: Iterable<String>): Builder
  }

  class BuildLauncherParametersBuilder : Builder(), TasksAwareBuilder {
    private val tasks = mutableListOf<String>()
    override fun withTasks(vararg args: String) = apply { tasks.addAll(args) }
    override fun withTasks(args: Iterable<String>) = apply { tasks.addAll(args) }
    override fun build(): BuildLauncherParameters {
      return BuildLauncherParameters(tasks, arguments, jvmArguments)
    }
  }

  class TestLauncherParametersBuilder : Builder() {
    override fun build(): TestLauncherParameters {
      return TestLauncherParameters(arguments, jvmArguments)
    }
  }

  class ModelBuilderParametersBuilder<T : Any?>(private val modelType: Class<T>) : Builder(), TasksAwareBuilder {
    private val tasks = mutableListOf<String>()
    override fun withTasks(vararg args: String) = apply { tasks.addAll(args) }
    override fun withTasks(args: Iterable<String>) = apply { tasks.addAll(args) }
    override fun build(): ModelBuilderParameters<T> {
      return ModelBuilderParameters(modelType, tasks, arguments, jvmArguments)
    }
  }

  class BuildActionParametersBuilder<T : Any?>(private val action: BuildAction<T?>) : Builder(), TasksAwareBuilder {
    private val tasks = mutableListOf<String>()
    override fun withTasks(vararg args: String) = apply { tasks.addAll(args) }
    override fun withTasks(args: Iterable<String>) = apply { tasks.addAll(args) }
    override fun build(): BuildActionParameters<T?> {
      return BuildActionParameters(action, tasks, arguments, jvmArguments)
    }
  }

  class PhasedBuildActionParametersBuilder<T : Any?>(private var projectsLoadedAction: BuildAction<T>?,
                                                     private var buildFinishedAction: BuildAction<T>?) : Builder(), TasksAwareBuilder {

    private val tasks = mutableListOf<String>()
    override fun withTasks(vararg args: String) = apply { tasks.addAll(args) }
    override fun withTasks(args: Iterable<String>) = apply { tasks.addAll(args) }
    override fun build(): PhasedBuildActionParameters<T> {
      return PhasedBuildActionParameters(projectsLoadedAction, buildFinishedAction, tasks, arguments, jvmArguments)
    }
  }
}

data class BuildLauncherParameters(val tasks: List<String>,
                                   override val arguments: List<String>,
                                   override val jvmArguments: List<String>) : TargetBuildParameters()

data class TestLauncherParameters(override val arguments: List<String>,
                                  override val jvmArguments: List<String>) : TargetBuildParameters()

data class ModelBuilderParameters<T>(val modelType: Class<T>,
                                     val tasks: List<String>,
                                     override val arguments: List<String>,
                                     override val jvmArguments: List<String>) : TargetBuildParameters()

data class BuildActionParameters<T>(val buildAction: BuildAction<T>,
                                    val tasks: List<String>,
                                    override val arguments: List<String>,
                                    override val jvmArguments: List<String>) : TargetBuildParameters()

data class PhasedBuildActionParameters<T>(val projectsLoadedAction: BuildAction<T>?,
                                          val buildFinishedAction: BuildAction<T>?,
                                          val tasks: List<String>,
                                          override val arguments: List<String>,
                                          override val jvmArguments: List<String>) : TargetBuildParameters()