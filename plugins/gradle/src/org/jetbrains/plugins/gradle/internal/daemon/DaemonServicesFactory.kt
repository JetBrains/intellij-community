// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DaemonServicesFactory")

package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.util.GradleVersion
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Optional

fun getDaemonServiceFactory(daemonClientFactory: DaemonClientFactory, myServiceDirectoryPath: String?): ServiceRegistry {
  val layoutParameters = getBuildLayoutParameters(myServiceDirectoryPath)
  val daemonParameters = getDaemonParameters(layoutParameters)
  return when {
    GradleVersionUtil.isCurrentGradleAtLeast("8.13") -> getDaemonServicesAfter8Dot13(daemonClientFactory, daemonParameters)
    GradleVersionUtil.isCurrentGradleAtLeast("8.8") -> getDaemonServicesAfter8Dot8(daemonClientFactory, daemonParameters)
    else -> getDaemonServicesBefore8Dot8(daemonClientFactory, daemonParameters)
  }
}

private fun getDaemonServicesBefore8Dot8(daemonClientFactory: DaemonClientFactory,
                                         parameters: DaemonParameters): ServiceRegistry {
  try {
    val method: Method = DaemonClientFactory::class.java.getDeclaredMethod("createBuildClientServices",
                                                                           OutputEventListener::class.java,
                                                                           DaemonParameters::class.java,
                                                                           InputStream::class.java
    )
    val invocationResult: Any = method.invoke(daemonClientFactory,
                                              OutputEventListener {},
                                              parameters,
                                              ByteArrayInputStream(ByteArray(0))
    )
    return invocationResult as ServiceRegistry
  }
  catch (e: ReflectiveOperationException) {
    throw RuntimeException("Cannot resolve ServiceRegistry by reflection. Gradle version: " + GradleVersion.current(), e)
  }
  catch (e: ClassCastException) {
    throw RuntimeException("Unable to cast the result of the invocation to ServiceRegistry. Gradle version: " + GradleVersion.current(), e)
  }
}

private fun getDaemonServicesAfter8Dot13(daemonClientFactory: DaemonClientFactory, parameters: DaemonParameters): ServiceRegistry {
  try {
    val daemonRequestContextClass = Class.forName("org.gradle.launcher.daemon.context.DaemonRequestContext")
    val serviceLookupClass = Class.forName("org.gradle.internal.service.ServiceLookup")
    val createBuildClientServicesMethod: Method = DaemonClientFactory::class.java.getDeclaredMethod(
      "createBuildClientServices",
      serviceLookupClass,
      DaemonParameters::class.java,
      daemonRequestContextClass,
      InputStream::class.java,
      Optional::class.java
    )
    val serviceLookupDelegate = getGradleServiceLookup()
    val serviceLookup: Any = GradleServiceLookupProxy.newProxyInstance(serviceLookupDelegate)
    val requestContext = getDaemonRequestContextAfter8Dot8()
    return createBuildClientServicesMethod.invoke(
      daemonClientFactory,
      serviceLookup,
      parameters,
      requestContext,
      ByteArrayInputStream(ByteArray(0)),
      Optional.empty<InternalBuildProgressListener>()
    ) as ServiceRegistry
  }
  catch (e: ReflectiveOperationException) {
    throw RuntimeException("Cannot resolve ServiceRegistry by reflection. Gradle version: " + GradleVersion.current(), e)
  }
  catch (e: ClassCastException) {
    throw RuntimeException("Unable to cast the result of the invocation to ServiceRegistry. Gradle version: " + GradleVersion.current(), e)
  }
}

private fun getDaemonServicesAfter8Dot8(daemonClientFactory: DaemonClientFactory, parameters: DaemonParameters): ServiceRegistry {
  try {
    val daemonRequestContextClass = Class.forName("org.gradle.launcher.daemon.context.DaemonRequestContext")
    val serviceLookupClass = Class.forName("org.gradle.internal.service.ServiceLookup")
    val createBuildClientServicesMethod: Method = DaemonClientFactory::class.java.getDeclaredMethod(
      "createBuildClientServices",
      serviceLookupClass,
      DaemonParameters::class.java,
      daemonRequestContextClass,
      InputStream::class.java
    )
    val serviceLookupDelegate = getGradleServiceLookup()
    val serviceLookup: Any = GradleServiceLookupProxy.newProxyInstance(serviceLookupDelegate)
    val requestContext = getDaemonRequestContextAfter8Dot8()
    return createBuildClientServicesMethod.invoke(
      daemonClientFactory,
      serviceLookup,
      parameters,
      requestContext,
      ByteArrayInputStream(ByteArray(0))
    ) as ServiceRegistry
  }
  catch (e: ReflectiveOperationException) {
    throw RuntimeException("Cannot resolve ServiceRegistry by reflection. Gradle version: " + GradleVersion.current(), e)
  }
  catch (e: ClassCastException) {
    throw RuntimeException("Unable to cast the result of the invocation to ServiceRegistry. Gradle version: " + GradleVersion.current(), e)
  }
}

private fun getBuildLayoutParameters(myServiceDirectoryPath: String?): BuildLayoutParameters {
  val layout = BuildLayoutParameters()
  if (!myServiceDirectoryPath.isNullOrEmpty()) {
    layout.setGradleUserHomeDir(File(myServiceDirectoryPath))
  }
  return layout
}

private fun getGradleServiceLookup(): GradleServiceLookup {
  val userInputReceiverClass = Class.forName("org.gradle.internal.logging.console.GlobalUserInputReceiver")
  val userInputReceiver = Proxy.newProxyInstance(
    userInputReceiverClass.classLoader,
    arrayOf(userInputReceiverClass),
    InvocationHandler { _, _, _ -> }
  )
  return GradleServiceLookup().apply {
    register(OutputEventListener::class.java, OutputEventListener.NO_OP)
    register(userInputReceiverClass, userInputReceiver)
  }
}

private fun getDaemonRequestContextAfter8Dot8(): Any {
  val requestContextClass = Class.forName("org.gradle.launcher.daemon.context.DaemonRequestContext")
  val nativeServicesClass = Class.forName("org.gradle.internal.nativeintegration.services.NativeServices")
  val nativeServicesModeClass = nativeServicesClass.declaredClasses.find { it.name.contains("NativeServicesMode") }
                                ?: throw IllegalStateException("The NativeServicesMode class is not found inside the NativeServices class. " +
                                                               "Gradle version: ${GradleVersion.current()}")
  if (!nativeServicesModeClass.isEnum) {
    throw IllegalStateException("NativeServicesMode is expected to be a Enum. Gradle version: ${GradleVersion.current()}")
  }
  val daemonJvmCriteriaClass = Class.forName("org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria")
  val nativeServiceModeValue = nativeServicesModeClass.enumConstants[2]
  if (GradleVersionUtil.isCurrentGradleAtLeast("8.10")) {
    val requestContextConstructor = requestContextClass.getDeclaredConstructor(
      daemonJvmCriteriaClass,
      Collection::class.java,
      Boolean::class.java,
      nativeServicesModeClass,
      DaemonPriority::class.java
    )
    return requestContextConstructor.newInstance(
      /*DaemonJvmCriteria*/ null,
      /*daemonOpts*/ emptyList<String>(),
      /*applyInstrumentationAgent*/ false,
      /*nativeServicesMode*/ nativeServiceModeValue,
      /*priority*/ DaemonPriority.NORMAL
    )
  }
  val legacyDaemonPriorityClass = Class.forName("org.gradle.launcher.daemon.configuration.DaemonParameters\$Priority")
  if (!legacyDaemonPriorityClass.isEnum) {
    throw IllegalStateException("DaemonParameters.Priority is expected to be a Enum. Gradle version: ${GradleVersion.current()}")
  }
  val normalDaemonPriority = legacyDaemonPriorityClass.enumConstants[1]
  if (GradleVersionUtil.isCurrentGradleAtLeast("8.9")) {
    val requestContextConstructor = requestContextClass.getDeclaredConstructor(
      daemonJvmCriteriaClass,
      Collection::class.java,
      Boolean::class.java,
      nativeServicesModeClass,
      legacyDaemonPriorityClass
    )
    return requestContextConstructor.newInstance(
      /*DaemonJvmCriteria*/ null,
      /*daemonOpts*/ emptyList<String>(),
      /*applyInstrumentationAgent*/ false,
      /*nativeServicesMode*/ nativeServiceModeValue,
      /*priority*/ normalDaemonPriority
    )
  }
  else {
    val requestContextConstructor = requestContextClass.getDeclaredConstructor(
      JavaInfo::class.java,
      daemonJvmCriteriaClass,
      Collection::class.java,
      Boolean::class.java,
      nativeServicesModeClass,
      legacyDaemonPriorityClass
    )
    return requestContextConstructor.newInstance(
      /*JavaInfo*/ null,
      /*DaemonJvmCriteria*/ null,
      /*daemonOpts*/ emptyList<String>(),
      /*applyInstrumentationAgent*/ false,
      /*nativeServicesMode*/ nativeServiceModeValue,
      /*priority*/ normalDaemonPriority
    )
  }
}
