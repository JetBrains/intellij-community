// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.gradle.toolingExtension.GradleToolingExtensionClass
import com.intellij.gradle.toolingExtension.impl.GradleToolingExtensionImplClass
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.Function
import com.intellij.util.ReflectionUtil.*
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.lang.UrlClassLoader
import it.unimi.dsi.fastutil.Hash
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.service.CloseableServiceRegistry
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.GradleConnectorFactory
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.*
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.function.BiConsumer


private val LOG = Logger.getInstance("org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices")

@JvmOverloads
fun stopDaemons(knownGradleUserHomes: Set<String?> = findKnownGradleUserHomes()) {
  forEachConnection(knownGradleUserHomes) { connection: ConsumerConnection, gradleUserHome: String? ->
    runAction(gradleUserHome, connection, DaemonStopAction::class.java, null)
  }
}

fun stopDaemons(knownGradleUserHomes: Set<String?> = findKnownGradleUserHomes(), daemons: List<DaemonState>) {
  val tokens = daemons.mapNotNull { it.token }
  forEachConnection(knownGradleUserHomes) { connection: ConsumerConnection, gradleUserHome: String? ->
    runAction(gradleUserHome, connection, DaemonStopAction::class.java, tokens)
  }
}

fun gracefulStopDaemons(knownGradleUserHomes: Set<String?> = findKnownGradleUserHomes()) {
  forEachConnection(knownGradleUserHomes) { connection: ConsumerConnection, gradleUserHome: String? ->
    runAction(gradleUserHome, connection, DaemonStopWhenIdleAction::class.java, null)
  }
}

@JvmOverloads
fun getDaemonsStatus(knownGradleUserHomes: Set<String?> = findKnownGradleUserHomes()): List<DaemonState> {
  val result: MutableList<DaemonState?> = ArrayList<DaemonState?>()
  forEachConnection(knownGradleUserHomes) { connection: ConsumerConnection, gradleUserHome: String? ->
    val daemonStates = runAction(gradleUserHome, connection, DaemonStatusAction::class.java,
                                 null) as List<DaemonState>?
    if (daemonStates != null && !daemonStates.isEmpty()) {
      result.addAll(daemonStates)
    }
  }
  return result.distinctBy { it?.pid }.filterNotNull()
}

@Throws(Exception::class)
private fun runActionWithDaemonClient(
  gradleUserHome: String?,
  daemonClientFactory: Any,
  actionClass: Class<*>,
  arg: Any?
): Any? {
  val daemonClientClassLoader = UrlClassLoader.build()
    .files(listOf<Path?>(
      PathManager.getJarForClass(actionClass),  // jars required for Gradle runtime utils

      PathManager.getJarForClass(GradleToolingExtensionClass::class.java),
      PathManager.getJarForClass(GradleToolingExtensionImplClass::class.java),  // jars required for i18n utils

      PathManager.getJarForClass(DynamicBundle::class.java),
      PathManager.getJarForClass(AbstractBundle::class.java),
      PathManager.getJarForClass(HashingStrategy::class.java),
      PathManager.getJarForClass(Hash::class.java),
      PathManager.getJarForClass(Function::class.java)
    ))
    .parent(daemonClientFactory.javaClass.getClassLoader())
    .allowLock(false)
    .get()

  val myRawArgData = getSerialized(arg)
  var myRawResultData: ByteArray? = null
  val oldClassLoader = Thread.currentThread().getContextClassLoader()
  try {
    Thread.currentThread().setContextClassLoader(daemonClientClassLoader)
    val loadedActionClazz = daemonClientClassLoader.loadClass(actionClass.getName())
    val argWithContextClassloader = getObject(myRawArgData)
    val runMethod = findRunMethod(loadedActionClazz, daemonClientFactory, argWithContextClassloader)
    val actionInstance: Any = loadedActionClazz.getDeclaredConstructor(String::class.java).newInstance(gradleUserHome)

    val result = if (arg == null) runMethod.invoke(actionInstance, daemonClientFactory)
    else runMethod.invoke(actionInstance, daemonClientFactory, argWithContextClassloader)
    if (result is Serializable) {
      myRawResultData = getSerialized(result)
    }
  }
  finally {
    Thread.currentThread().setContextClassLoader(oldClassLoader)
  }
  if (myRawResultData != null) {
    return getObject(myRawResultData)
  }
  return null
}

@Throws(Exception::class)
private fun findRunMethod(clazz: Class<*>, daemonClientFactory: Any, arg: Any?): Method {
  if (arg == null) {
    return clazz.getMethod("run", daemonClientFactory.javaClass)
  }
  var method: Method? = null
  try {
    method = clazz.getMethod("run", daemonClientFactory.javaClass, arg.javaClass)
  }
  catch (_: Exception) {
  }
  if (method == null) {
    val interfaces = arg.javaClass.interfaces
    for (cl in interfaces) {
      try {
        method = clazz.getMethod("run", daemonClientFactory.javaClass, cl)
        break
      }
      catch (_: Exception) {
      }
    }
  }
  return method!!
}

@Throws(IOException::class)
private fun getSerialized(obj: Any?): ByteArray? {
  if (obj is Serializable) {
    val bOut = ByteArrayOutputStream()
    val oOut = ObjectOutputStream(bOut)
    try {
      oOut.writeObject(obj)
      return bOut.toByteArray()
    }
    finally {
      oOut.close()
    }
  }
  return null
}

private fun getObject(bytes: ByteArray?): Any? {
  if (bytes != null) {
    try {
      return ObjectInputStream(ByteArrayInputStream(bytes)).readObject()
    }
    catch (_: Exception) {
    }
  }
  return null
}

fun getConnections() : Map<ClassPath, ConsumerConnection> {
  val sharedConnectorFactory = getStaticFieldValue(
    ConnectorServices::class.java,
    GradleConnectorFactory::class.java,
    "sharedConnectorFactory"
  ) as GradleConnectorFactory
  val defaultGradleConnectorFactoryClass = ConnectorServices::class.java.declaredClasses
    .find { it.canonicalName == "org.gradle.tooling.internal.consumer.ConnectorServices.DefaultGradleConnectorFactory" }
  if (defaultGradleConnectorFactoryClass == null) {
    LOG.warn("Unable to find the DefaultGradleConnectorFactory class in the Tooling API")
    return emptyMap()
  }
  val registry: CloseableServiceRegistry = getField(
    defaultGradleConnectorFactoryClass,
    sharedConnectorFactory,
    CloseableServiceRegistry::class.java,
    "ownerRegistry"
  )
  val loader = registry.get(ToolingImplementationLoader::class.java)
  val delegate = getField(SynchronizedToolingImplementationLoader::class.java,
                          loader,
                          ToolingImplementationLoader::class.java,
                          "delegate")

  val connections = getField(CachingToolingImplementationLoader::class.java,
                             delegate,
                             Any::class.java,
                             "connections")
  if (connections == null) {
    LOG.warn("There are no 'connections' field in ${delegate::class.java.canonicalName}")
    return emptyMap()
  }
  return when {
    Map::class.java.isAssignableFrom(connections::class.java) -> connections as Map<ClassPath, ConsumerConnection>
    isGuavaCache(connections) -> tryExtractCachedConnections(connections)
    else -> {
      LOG.warn("Unable to determine the type of the 'collections' field in ${delegate::class.java.canonicalName}")
      return emptyMap()
    }
  }
}

private fun isGuavaCache(field: Any): Boolean {
  try {
    // this trick is required to prevent class cast exception and other side effects of the field being an instance of a re-packaged class
    Class.forName("org.gradle.internal.impldep.com.google.common.cache.Cache").isAssignableFrom(field::class.java)
  }
  catch (_: Exception) {
    return false
  }
  return true
}

private fun tryExtractCachedConnections(connections: /*org.gradle.internal.impldep.com.google.common.cache.Cache*/ Any)
  : Map<ClassPath, ConsumerConnection> {
  try {
    val cacheClass = Class.forName("org.gradle.internal.impldep.com.google.common.cache.Cache")
    val getter = cacheClass.getDeclaredMethod("asMap")
    return getter.invoke(connections) as Map<ClassPath, ConsumerConnection>
  }
  catch (e: Exception) {
    LOG.error("Unable to extract connections from the delegate", e)
    return emptyMap()
  }
}

private fun runAction(gradleUserHome: String?, connection: ConsumerConnection, actionClass: Class<*>, arg: Any?): Any? {
  try {
    val daemonClientFactory = obtainDaemonClientFactory(connection) ?: return null
    return runActionWithDaemonClient(gradleUserHome, daemonClientFactory, actionClass, arg)
  }
  catch (t: Throwable) {
    LOG.warn("Unable to send daemon message for " + connection.displayName)
    if (LOG.isDebugEnabled()) {
      LOG.debug(t)
    }
    else {
      @Suppress("HardCodedStringLiteral")
      LOG.warn(ExceptionUtil.getNonEmptyMessage(ExceptionUtil.getRootCause(t), "Unable to send daemon message"))
    }
  }
  return null
}

@Throws(Exception::class)
private fun obtainDaemonClientFactory(connection: ConsumerConnection?): Any? {
  // connection.delegate.delegate.connection.daemonClientFactory
  if (connection is ParameterValidatingConsumerConnection) {
    val delegate = getField<ConsumerConnection?>(ParameterValidatingConsumerConnection::class.java, connection,
                                                 ConsumerConnection::class.java, "delegate")
    if (delegate is AbstractConsumerConnection) {
      val connectionVersion4 = delegate.delegate
      val providerConnectionField = connectionVersion4.javaClass.getDeclaredField("connection")
      providerConnectionField.setAccessible(true)
      val providerConnection = getFieldValue<Any?>(providerConnectionField, connectionVersion4)

      val daemonClientFactoryField = providerConnection!!.javaClass.getDeclaredField("daemonClientFactory")
      daemonClientFactoryField.setAccessible(true)

      return getFieldValue<Any>(daemonClientFactoryField, providerConnection)!!
    }
  }
  return null
}

private fun forEachConnection(knownGradleUserHomes: Set<String?>, closure: BiConsumer<ConsumerConnection, String?>) {
  val connections: Map<ClassPath, ConsumerConnection> = getConnections()
  for (conn in connections.values) {
    for (gradleUserHome in knownGradleUserHomes) {
      closure.accept(conn, StringUtil.nullize(gradleUserHome))
    }
  }
}

private fun findKnownGradleUserHomes(): Set<String> {
  val projectManager = ProjectManager.getInstanceIfCreated() ?: return setOf<String>("")
  val gradleUserHomes = projectManager.openProjects
    .filter { !it.isDisposed }
    .map { GradleSettings.getInstance(it).serviceDirectoryPath ?: "" }

  // add "" to always search for Gradle connections in the default Gradle user home
  return (gradleUserHomes + "").toSet()
}
