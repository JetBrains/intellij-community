// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.Function
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.lang.UrlClassLoader
import gnu.trove.TObjectHashingStrategy
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import it.unimi.dsi.fastutil.Hash
import org.gradle.internal.classpath.ClassPath
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleSettings

import java.lang.reflect.Method

@ApiStatus.Internal
@CompileStatic
class GradleDaemonServices {
  private static final Logger LOG = Logger.getInstance(GradleDaemonServices.class)

  static void stopDaemons() {
    forEachConnection { ConsumerConnection connection, String gradleUserHome ->
      runAction(gradleUserHome, connection, DaemonStopAction, null)
    }
  }

  static void stopDaemons(List<DaemonState> daemons) {
    List<byte[]> tokens = new ArrayList<>()
    daemons.each { if (it.token) tokens.add(it.token) }
    forEachConnection { ConsumerConnection connection, String gradleUserHome ->
      runAction(gradleUserHome, connection, DaemonStopAction, tokens)
    }
  }

  static List<DaemonState> getDaemonsStatus() {
    List<DaemonState> result = new ArrayList<>()
    forEachConnection { ConsumerConnection connection, String gradleUserHome ->
      List<DaemonState> daemonStates = runAction(gradleUserHome, connection, DaemonStatusAction, null) as List<DaemonState>
      if (daemonStates) {
        result.addAll(daemonStates)
      }
    }
    return result
  }

  private static Object runAction(String gradleUserHome,
                                  Object daemonClientFactory,
                                  ConsumerConnection connection,
                                  Class actionClass,
                                  Object arg) {
    def daemonClientClassLoader = UrlClassLoader.build()
      .files(List.of(
        PathManager.getJarForClass(actionClass),

        // jars required for i18n utils
        PathManager.getJarForClass(DynamicBundle),
        PathManager.getJarForClass(AbstractBundle),
        PathManager.getJarForClass(TObjectHashingStrategy),
        PathManager.getJarForClass(HashingStrategy),
        PathManager.getJarForClass(Hash),
        PathManager.getJarForClass(Function)
      ))
      .parent(daemonClientFactory.class.classLoader)
      .allowLock(false)
      .get()

    def myRawArgData = getSerialized(arg)
    byte[] myRawResultData = null
    final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader()
    try {
      Thread.currentThread().setContextClassLoader(daemonClientClassLoader)
      Class<?> clazz = daemonClientClassLoader.loadClass(actionClass.name)
      def _arg = getObject(myRawArgData)
      Method method = findMethod(clazz, daemonClientFactory, _arg)
      Object[] gradleUserHomeParam = [gradleUserHome]
      def result = arg == null ? method.invoke(clazz.newInstance(gradleUserHomeParam), daemonClientFactory) :
                   method.invoke(clazz.newInstance(gradleUserHomeParam), daemonClientFactory, _arg)
      if (result instanceof Serializable) {
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

  private static Method findMethod(Class<?> clazz, daemonClientFactory, arg) {
    if (!arg) {
      return clazz.getMethod("run", daemonClientFactory.class)
    }
    Method method = null
    try {
      method = clazz.getMethod("run", daemonClientFactory.class, arg.class)
    }
    catch (ignore) {
    }
    if (method == null) {
      def interfaces = arg.class.interfaces
      for (Class cl in interfaces) {
        try {
          method = clazz.getMethod("run", daemonClientFactory.class, cl)
          break
        }
        catch (ignore) {
        }
      }
    }
    return method
  }

  private static byte[] getSerialized(obj) {
    if (obj instanceof Serializable) {
      ByteArrayOutputStream bOut = new ByteArrayOutputStream()
      ObjectOutputStream oOut = new ObjectOutputStream(bOut)
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

  private static Object getObject(byte[] bytes) {
    if (bytes != null) {
      try {
        return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject()
      }
      catch (ignore) { }
    }
    return null
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static Map<ClassPath, ConsumerConnection> getConnections() {
    def registry = ConnectorServices.singletonRegistry
    if (registry.closed) {
      return Collections.emptyMap()
    }
    def loader = registry.get(ToolingImplementationLoader.class) as SynchronizedToolingImplementationLoader
    def delegate = loader.delegate as CachingToolingImplementationLoader
    return delegate.connections
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static Object runAction(String gradleUserHome, ConsumerConnection connection, Class actionClass, Object arg) {
    try {
      def daemonClientFactory = connection.delegate.delegate.connection.daemonClientFactory
      runAction(gradleUserHome, daemonClientFactory, connection, actionClass, arg)
    }
    catch (Throwable t) {
      LOG.warn("Unable to send daemon message for " + connection.getDisplayName())
      if (LOG.isDebugEnabled()) {
        LOG.debug(t)
      }
      else {
        LOG.warn(ExceptionUtil.getNonEmptyMessage(ExceptionUtil.getRootCause(t), "Unable to send daemon message"))
      }
    }
  }

  private static void forEachConnection(Closure closure) {
    Set<String> gradleUserHomes = getGradleUserHomes()
    Map<ClassPath, ConsumerConnection> connections = getConnections()
    for (conn in connections.values()) {
      for (String gradleUserHome in gradleUserHomes) {
        closure.call(conn, StringUtil.nullize(gradleUserHome))
      }
    }
  }

  private static Set<String> getGradleUserHomes() {
    def projectManager = ProjectManager.getInstanceIfCreated()
    if (projectManager == null) return Set.of("")
    Set<String> gradleUserHomes = new HashSet()
    for (project in projectManager.openProjects) {
      def gradleUserHome = GradleSettings.getInstance(project).getServiceDirectoryPath()
      gradleUserHomes.add(StringUtil.notNullize(gradleUserHome))
    }
    gradleUserHomes
  }

  //these methods are added to work around compilation problems with Groovy 3: these methods get default implementation in GroovyObject interface,
  //and stub generator doesn't add implementations for them, but here Groovy 2.4 library from intellij.gradle.toolingExtension module is used
  //where default implementations are absent
  Object invokeMethod(String name, Object args) {
    return super.invokeMethod(name, args)
  }

  @CompileDynamic
  Object getProperty(String propertyName) {
    return super.getProperty(propertyName)
  }

  @CompileDynamic
  void setProperty(String propertyName, Object newValue) {
    super.setProperty(propertyName, newValue)
  }

  MetaClass getMetaClass() {
    return super.getMetaClass()
  }

  void setMetaClass(MetaClass metaClass) {
    super.setMetaClass(metaClass)
  }
}
