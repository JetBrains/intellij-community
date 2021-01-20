// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.DefaultBuildOperationIdFactory
import org.gradle.internal.time.Time
import org.gradle.tooling.BuildAction
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.*
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.parameters.FailsafeBuildProgressListenerAdapter
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import java.lang.reflect.Field

fun createConnector(buildEventConsumer: BuildEventConsumer): GradleConnector {
  val toolingImplementationLoader: ToolingImplementationLoader = MyToolingImplementationLoader(
    buildEventConsumer,
    SynchronizedToolingImplementationLoader(CachingToolingImplementationLoader(DefaultToolingImplementationLoader()))
  )
  val executorFactory: ExecutorFactory = DefaultExecutorFactory()
  val buildOperationIdFactory: BuildOperationIdFactory = DefaultBuildOperationIdFactory()
  val loggingProvider: LoggingProvider = SynchronizedLogging(Time.clock(), buildOperationIdFactory)
  val connectionFactory = ConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider)
  val distributionFactory = DistributionFactory(Time.clock())
  return DefaultGradleConnector(connectionFactory, distributionFactory)
}

private fun attachListenerIfNeeded(parameters: ConsumerOperationParameters, buildEventConsumer: BuildEventConsumer) {
  val progressListener = getField(ConsumerOperationParameters::class.java, parameters,
                                  FailsafeBuildProgressListenerAdapter::class.java, "buildProgressListener")!!
  if (progressListener is MyFailsafeBuildProgressListenerAdapter) {
    return
  }
  setField(ConsumerOperationParameters::class.java, parameters,
           FailsafeBuildProgressListenerAdapter::class.java, "buildProgressListener",
           MyFailsafeBuildProgressListenerAdapter(buildEventConsumer, progressListener))
}

private fun <T> getField(objectClass: Class<*>,
                         instance: Any?,
                         fieldType: Class<T>,
                         fieldName: String): T? {
  try {
    val field = findField(objectClass, fieldType, fieldName) ?: return null
    @Suppress("UNCHECKED_CAST")
    return field[instance] as T?
  }
  catch (ignored: IllegalAccessException) {
  }
  return null
}

private fun <T> findField(objectClass: Class<*>, fieldType: Class<T>, fieldName: String): Field? {
  for (field in objectClass.declaredFields) {
    if (fieldName == field.name && fieldType.isAssignableFrom(field.type)) {
      field.isAccessible = true
      return field
    }
  }
  return null
}

/**
 * @return true if value was set
 */
private fun <T> setField(objectClass: Class<*>,
                         instance: Any?,
                         fieldType: Class<T>,
                         fieldName: String,
                         value: T): Boolean {
  try {
    val field = findField(objectClass, fieldType, fieldName)
    if (field != null) {
      field[instance] = value
      return true
    }
  }
  catch (ignored: IllegalAccessException) {
  }
  return false
}

private class MyToolingImplementationLoader(private val buildEventConsumer: BuildEventConsumer,
                                            private val delegate: ToolingImplementationLoader) : ToolingImplementationLoader {
  override fun create(distribution: Distribution,
                      factory: ProgressLoggerFactory,
                      listener: InternalBuildProgressListener,
                      parameters: ConnectionParameters,
                      token: BuildCancellationToken): ConsumerConnection {
    val consumerConnection = delegate.create(distribution, factory, object : InternalBuildProgressListener {
      override fun onEvent(o: Any) {
        buildEventConsumer.dispatch(o)
        listener.onEvent(o)
      }

      override fun getSubscribedOperations(): List<String> {
        return listener.subscribedOperations
      }
    }, parameters, token)
    return object : ConsumerConnection {
      override fun stop() {
        consumerConnection.stop()
      }

      override fun getDisplayName(): String {
        return consumerConnection.displayName
      }

      @Throws(UnsupportedOperationException::class, IllegalStateException::class)
      override fun <T> run(aClass: Class<T>, parameters: ConsumerOperationParameters): T {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        return consumerConnection.run(aClass, parameters)
      }

      @Throws(UnsupportedOperationException::class, IllegalStateException::class)
      override fun <T> run(action: BuildAction<T>, parameters: ConsumerOperationParameters): T {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        return consumerConnection.run(action, parameters)
      }

      @Throws(UnsupportedOperationException::class, IllegalStateException::class)
      override fun run(action: PhasedBuildAction, parameters: ConsumerOperationParameters) {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        consumerConnection.run(action, parameters)
      }

      override fun runTests(request: TestExecutionRequest, parameters: ConsumerOperationParameters) {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        consumerConnection.runTests(request, parameters)
      }

      override fun notifyDaemonsAboutChangedPaths(list: List<String>, parameters: ConsumerOperationParameters) {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        consumerConnection.notifyDaemonsAboutChangedPaths(list, parameters)
      }

      override fun stopWhenIdle(parameters: ConsumerOperationParameters) {
        attachListenerIfNeeded(parameters, buildEventConsumer)
        consumerConnection.stopWhenIdle(parameters)
      }
    }
  }
}

private class MyFailsafeBuildProgressListenerAdapter(private val buildEventConsumer: BuildEventConsumer,
                                                     private val adapter: FailsafeBuildProgressListenerAdapter) :
  FailsafeBuildProgressListenerAdapter(adapter) {
  override fun onEvent(event: Any) {
    buildEventConsumer.dispatch(event)
    adapter.onEvent(event)
  }

  override fun rethrowErrors() {
    adapter.rethrowErrors()
  }
}