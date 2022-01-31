// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.util.UserDataHolderBase
import org.gradle.tooling.*
import org.gradle.tooling.internal.consumer.PhasedBuildAction.BuildActionWrapper
import org.gradle.tooling.internal.consumer.ProjectConnectionCloseListener
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
internal class TargetProjectConnection(val environmentConfigurationProvider: TargetEnvironmentConfigurationProvider,
                                       val taskId: ExternalSystemTaskId?,
                                       val taskListener: ExternalSystemTaskNotificationListener?,
                                       val distribution: TargetGradleDistribution,
                                       val parameters: TargetConnectionParameters,
                                       private val connectionCloseListener: ProjectConnectionCloseListener?) : ProjectConnection, UserDataHolderBase() {
  override fun close() {
    connectionCloseListener?.connectionClosed(this)
  }

  override fun <T : Any?> getModel(modelType: Class<T>): T = model(modelType).get()
  override fun <T : Any?> getModel(modelType: Class<T>, resultHandler: ResultHandler<in T>) = model(modelType).get(resultHandler)
  override fun newBuild(): BuildLauncher = TargetBuildLauncher(this)

  override fun newTestLauncher(): TestLauncher {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> model(modelType: Class<T>): ModelBuilder<T> {
    require(modelType.isInterface) { "Cannot fetch a model of type '${modelType.name}' as this type is not an interface." }
    return TargetModelBuilder(this, modelType)
  }

  override fun <T : Any?> action(buildAction: BuildAction<T?>): BuildActionExecuter<T> = TargetBuildActionExecuter(this, buildAction)

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

      override fun build(): BuildActionExecuter<Void> = TargetPhasedBuildActionExecuter(this@TargetProjectConnection,
                                                                                        projectsLoadedAction, buildFinishedAction)
    }
  }

  override fun notifyDaemonsAboutChangedPaths(p0: MutableList<Path>?) {
  // TODO: implement passing information about recent file changes to a Gradle daemon
  }

  fun disconnect() {
    close()
    clearUserData()
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