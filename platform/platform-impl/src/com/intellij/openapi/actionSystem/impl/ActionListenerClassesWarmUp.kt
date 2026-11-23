// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.ApplicationActivity
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.impl.SeparatedCommandListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.serviceContainer.isListenerApplicable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.VisibleForTesting

private val LOG = logger<ActionListenerClassesWarmUp>()

@VisibleForTesting
internal val WARM_UP_LISTENER_TOPICS: List<String> = listOf(
  SeparatedCommandListener::class.java.name,
  AnActionListener::class.java.name,
)

/**
 * Loads declarative listener classes in the background at start-up.
 */
internal class ActionListenerClassesWarmUp : ApplicationActivity {
  override suspend fun execute() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    for (topicClassName in WARM_UP_LISTENER_TOPICS) {
      warmUpLazyListenerClasses(topicClassName)
    }
  }
}

/**
 * Loads w/o initialization the classes of the lazy listeners of the topic with the [topicClassName].
 * The project listeners load once.
 */
@RequiresBackgroundThread
internal suspend fun warmUpLazyListenerClasses(topicClassName: String) {
  val classLoaders = lazyListenerClassLoaders(topicClassName)
  var missed = 0
  for ([name, classLoader] in classLoaders) {
    currentCoroutineContext().ensureActive()
    if (!loadListenerClass(name, classLoader)) {
      missed++
    }
  }
  if (missed > 0) {
    LOG.info("$missed of ${classLoaders.size} listener classes of $topicClassName did not load in the warm-up")
  }
}

/**
 * Maps the class names of the lazy listeners of the topic with the [topicClassName] to the class loaders of their modules.
 * Skips the listeners that the container does not register.
 */
@VisibleForTesting
internal fun lazyListenerClassLoaders(topicClassName: String): Map<String, ClassLoader> {
  val app = ApplicationManager.getApplication()
  val defaultClassLoader = ActionListenerClassesWarmUp::class.java.classLoader
  val classLoaders = LinkedHashMap<String, ClassLoader>()
  for (module in PluginManagerCore.getPluginSet().sequenceResolvedSortedDescriptorsForRegistration()) {
    val classLoader = module.pluginClassLoader ?: defaultClassLoader
    for (listener in module.appContainerDescriptor.listeners + module.projectContainerDescriptor.listeners) {
      if (listener.topicClassName == topicClassName &&
          isListenerApplicable(listener, isUnitTestMode = app.isUnitTestMode, isHeadless = app.isHeadlessEnvironment)) {
        classLoaders.putIfAbsent(listener.listenerClassName, classLoader)
      }
    }
  }
  return classLoaders
}

@VisibleForTesting
internal fun loadListenerClass(name: String, classLoader: ClassLoader): Boolean {
  try {
    val aClass = if (classLoader is PluginAwareClassLoader) {
      classLoader.tryLoadingClass(name, true) ?: return false
    }
    else {
      Class.forName(name, false, classLoader)
    }
    aClass.declaredConstructors
    return true
  }
  catch (e: ClassNotFoundException) {
    LOG.debug { "Cannot find the listener class $name: $e" }
  }
  catch (e: LinkageError) {
    LOG.debug { "Cannot link the listener class $name: $e" }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot load the listener class $name", e)
  }
  return false
}
