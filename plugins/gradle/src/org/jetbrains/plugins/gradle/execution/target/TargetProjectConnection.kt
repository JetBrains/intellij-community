// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.util.PathMapper
import org.gradle.tooling.*
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.PhasedBuildAction.BuildActionWrapper
import org.gradle.tooling.internal.consumer.ProjectConnectionCloseListener
import java.nio.file.Path

class TargetProjectConnection(private val environmentConfiguration: TargetEnvironmentConfiguration,
                              private val targetPathMapper: PathMapper?,
                              private val taskId: ExternalSystemTaskId?,
                              private val taskListener: ExternalSystemTaskNotificationListener?,
                              private val parameters: ConnectionParameters,
                              private val connectionCloseListener: ProjectConnectionCloseListener?) : ProjectConnection {
  override fun close() {
    connectionCloseListener?.connectionClosed(this)
  }

  override fun <T : Any?> getModel(modelType: Class<T>): T = model(modelType).get()
  override fun <T : Any?> getModel(modelType: Class<T>, resultHandler: ResultHandler<in T>) = model(modelType).get(resultHandler)
  override fun newBuild(): BuildLauncher = TargetBuildLauncher(environmentConfiguration, targetPathMapper, taskId, taskListener, parameters)

  override fun newTestLauncher(): TestLauncher {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> model(modelType: Class<T>): ModelBuilder<T> {
    require(modelType.isInterface) { "Cannot fetch a model of type '${modelType.name}' as this type is not an interface." }
    return TargetModelBuilder(environmentConfiguration, targetPathMapper, taskId, taskListener, parameters, modelType)
  }

  override fun <T : Any?> action(buildAction: BuildAction<T?>): BuildActionExecuter<T> =
    TargetBuildActionExecuter(environmentConfiguration, targetPathMapper, taskId, taskListener, parameters, buildAction)

  override fun action(): BuildActionExecuter.Builder {
    return object : BuildActionExecuter.Builder {
      private var projectsLoadedAction: BuildActionWrapper<Any>? = null
      private var buildFinishedAction: BuildActionWrapper<Any>? = null

      override fun <T : Any?> projectsLoaded(buildAction: BuildAction<T>,
                                             resultHandler: IntermediateResultHandler<in T>) = also {
        @Suppress("UNCHECKED_CAST")
        projectsLoadedAction = DefaultBuildActionWrapper(buildAction as BuildAction<Any>, resultHandler as IntermediateResultHandler<Any>)
      }

      override fun <T : Any?> buildFinished(buildAction: BuildAction<T>,
                                            resultHandler: IntermediateResultHandler<in T>) = also {
        @Suppress("UNCHECKED_CAST")
        buildFinishedAction = DefaultBuildActionWrapper(buildAction as BuildAction<Any>, resultHandler as IntermediateResultHandler<Any>)
      }

      override fun build(): BuildActionExecuter<Void> = TargetPhasedBuildActionExecuter(environmentConfiguration,
                                                                                        targetPathMapper,
                                                                                        taskId,
                                                                                        taskListener,
                                                                                        parameters,
                                                                                        projectsLoadedAction,
                                                                                        buildFinishedAction)
    }
  }

  override fun notifyDaemonsAboutChangedPaths(p0: MutableList<Path>?) {
    TODO("Not yet implemented")
  }

  fun disconnect() {
  }

  internal class DefaultBuildActionWrapper<T>(private val buildAction: BuildAction<T>,
                                              private val resultHandler: IntermediateResultHandler<T>) : BuildActionWrapper<T> {
    override fun getAction(): BuildAction<T> {
      return buildAction
    }

    override fun getHandler(): IntermediateResultHandler<in T> {
      return resultHandler
    }
  }
}