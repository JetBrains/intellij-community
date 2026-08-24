// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.jetbrains.plugins.gradle.connection.GradleConnectorService
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContextImpl
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.readText

internal class OutdatedIntelliJPlatformGradlePluginVersionCheckerTest : LightJavaCodeInsightFixtureTestCase() {

  fun testRequestsFreshGradleModel() {
    val fetchedModel = object : IntelliJPlatformGradleModel {
      override fun getDependencyHelperProductCodes() = emptyMap<String, String>()
      override fun getProductReleasesFile(): String? = null
      override fun getCurrentPluginVersion() = "1.0.0"
      override fun getLatestPluginVersion() = "2.0.0"
    }
    var requestedModelClass: Class<*>? = null
    var modelArguments = emptyList<String>()
    val modelBuilder = interfaceProxy<ModelBuilder<IntelliJPlatformGradleModel>> { proxy, method, arguments ->
      when (method.name) {
        "get" -> fetchedModel
        "withArguments" -> {
          modelArguments = when (val value = arguments?.single()) {
            is Iterable<*> -> value.filterIsInstance<String>()
            is Array<*> -> value.filterIsInstance<String>()
            else -> emptyList()
          }
          proxy
        }
        else -> proxy
      }
    }
    val connection = interfaceProxy<ProjectConnection> { _, method, arguments ->
      if (method.name == "model") {
        requestedModelClass = arguments?.single() as Class<*>
        modelBuilder
      }
      else null
    }
    val connectorService = object : GradleConnectorService {
      override fun getKnownGradleUserHomes(): Set<String> = emptySet()

      override fun <R> withGradleConnection(
        projectPath: String,
        taskId: ExternalSystemTaskId?,
        executionSettings: GradleExecutionSettings?,
        listener: ExternalSystemTaskNotificationListener?,
        cancellationToken: CancellationToken?,
        function: Function<ProjectConnection, R>,
      ): R = function.apply(connection)
    }
    project.replaceService(GradleConnectorService::class.java, connectorService, testRootDisposable)
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (!extensionArea.hasExtensionPoint(GradleExecutionHelperExtension.EP_NAME)) {
      extensionArea.registerExtensionPoint(
        GradleExecutionHelperExtension.EP_NAME.name,
        GradleExecutionHelperExtension::class.java.name,
        ExtensionPoint.Kind.INTERFACE,
        true,
      )
      Disposer.register(testRootDisposable) {
        extensionArea.unregisterExtensionPoint(GradleExecutionHelperExtension.EP_NAME.name)
      }
    }

    val events = mutableListOf<ExternalSystemTaskNotificationEvent>()
    val listener = object : ExternalSystemTaskNotificationListener {
      override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        events += event
      }
    }
    val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val context = GradleExecutionContextImpl(
      myFixture.tempDirPath,
      taskId,
      GradleExecutionSettings(),
      listener,
      GradleConnector.newCancellationTokenSource().token(),
    )
    val gradleEnvironment = interfaceProxy<GradleEnvironment> { _, method, _ ->
      if (method.name == "getGradleVersion") "8.13" else null
    }
    val buildIdentifier = interfaceProxy<BuildIdentifier> { _, method, _ ->
      if (method.name == "getRootDir") java.io.File(myFixture.tempDirPath) else null
    }
    val buildEnvironment = interfaceProxy<BuildEnvironment> { _, method, _ ->
      when (method.name) {
        "getBuildIdentifier" -> buildIdentifier
        "getGradle" -> gradleEnvironment
        else -> null
      }
    }

    OutdatedIntelliJPlatformGradlePluginVersionChecker().checkExecution(context, buildEnvironment)

    assertSame(IntelliJPlatformGradleModel::class.java, requestedModelClass)
    val initScriptPaths = modelArguments.mapIndexedNotNull { index, argument ->
      modelArguments.getOrNull(index + 1)?.takeIf { argument == GradleConstants.INIT_SCRIPT_CMD_OPTION }
    }
    assertSize(2, initScriptPaths)
    assertTrue(Path.of(initScriptPaths[0]).readText().contains("ext.mapPath"))
    assertTrue(Path.of(initScriptPaths[1]).readText().contains("classpath files([mapPath"))
    assertSize(1, events)
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T> interfaceProxy(
    crossinline handler: (T, Method, Array<out Any?>?) -> Any?,
  ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, arguments ->
    when (method.name) {
      "equals" -> proxy === arguments?.singleOrNull()
      "hashCode" -> System.identityHashCode(proxy)
      "toString" -> "${T::class.java.simpleName} proxy"
      else -> handler(proxy as T, method, arguments)
    }
  } as T
}
