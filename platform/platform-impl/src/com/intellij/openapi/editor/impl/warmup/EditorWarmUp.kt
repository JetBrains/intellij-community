// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.startup.impl.isStartupUiGateEnabled
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private object EditorWarmUp

private val LOG = logger<EditorWarmUp>()

private const val EDITOR_CLASSES_PATH = "com/intellij/openapi/editor/impl/warmup/editor-classes.txt"

/** The warm-up class list of the editor implementation. */
internal val EDITOR_CLASSES: WarmUpClassList by lazy {
  WarmUpClassList.read(EditorWarmUp::class.java.classLoader, EDITOR_CLASSES_PATH)
}

/**
 * Loads the classes and the bundles that the EDT otherwise loads when it creates the first editor.
 * The steps come in the order of their use on the EDT.
 * When [isStartupUiGateEnabled], the warm-up skips the load-only classes, because
 * [com.intellij.ide.startup.impl.preloadStartupUiClasses] loads them earlier.
 */
@RequiresBackgroundThread
internal suspend fun warmUpEditorClasses(listenerPoint: ExtensionPointName<EditorFactoryListener>) {
  warmUpClasses(EDITOR_CLASSES, EditorWarmUp::class.java.classLoader, includeLoadOnly = !isStartupUiGateEnabled())
  warmUpInspectionWidgetBundles()
  warmUpListenerClasses(listenerPoint)
}

/**
 * Initializes the [WarmUpClassList.initialize] classes of the [list] through the [classLoader].
 * With [includeLoadOnly], it also loads and links the [WarmUpClassList.loadOnly] classes.
 * The warm-up skips a missing class and logs it at the debug level.
 */
private suspend fun warmUpClasses(list: WarmUpClassList, classLoader: ClassLoader, includeLoadOnly: Boolean) {
  for (className in list.initialize) {
    prepareClass(className = className, initialize = true, classLoader = classLoader)
  }
  if (!includeLoadOnly) {
    return
  }
  for (className in list.loadOnly) {
    prepareClass(className = className, initialize = false, classLoader = classLoader)
  }
}

private suspend fun prepareClass(className: String, initialize: Boolean, classLoader: ClassLoader) {
  currentCoroutineContext().ensureActive()
  try {
    Class.forName(className, initialize, classLoader).declaredConstructors
  }
  catch (e: ClassNotFoundException) {
    LOG.debug("Cannot load warm-up class: $className", e)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot prepare warm-up class: $className", e)
  }
}

/**
 * Reads the bundles of the inspection widget. [com.intellij.openapi.editor.impl.EditorMarkupModelImpl] creates the widget actions
 * with each editor. The first read of a bundle reads the bundle file, and with a non-English locale it also looks for the localized files.
 */
private fun warmUpInspectionWidgetBundles() {
  try {
    EditorBundle.message("iw.compact.view")
    DaemonBundle.message("iw.inspection.cog.tooltip")
    LangBundle.message("action.ReaderModeProvider.text")
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot read the inspection widget bundles", e)
  }
}

/**
 * Loads the classes of the registered [EditorFactoryListener] extensions through the class loaders of their plugins.
 * The extensions stay uncreated. The EDT creates them when it notifies the listeners about the first editor.
 */
private suspend fun warmUpListenerClasses(listenerPoint: ExtensionPointName<EditorFactoryListener>) {
  for (extension in listenerPoint.filterableLazySequence()) {
    currentCoroutineContext().ensureActive()
    try {
      extension.implementationClass?.declaredConstructors
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot prepare editor factory listener class: ${extension.implementationClassName}", e)
    }
  }
}
