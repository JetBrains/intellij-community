// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private object EditorWarmUp

private val LOG = logger<EditorWarmUp>()

private const val EDITOR_CLASSES_PATH = "com/intellij/openapi/editor/impl/warmup/editor-classes.txt"

// Access to InspectionWidgetActionProvider.EP_NAME initializes it
private val INSPECTION_WIDGET_ACTION_PROVIDER_EP =
  ExtensionPointName<InspectionWidgetActionProvider>("com.intellij.iw.actionProvider")

/**
 * The warm-up class list of the editor implementation.
 * [com.intellij.ide.startup.impl.preloadStartupUiClasses] also loads it, so the startup UI list leaves these classes out.
 */
internal val EDITOR_CLASSES: WarmUpClassList by lazy {
  WarmUpClassList.read(EditorWarmUp::class.java.classLoader, EDITOR_CLASSES_PATH)
}

/**
 * Loads the classes and the bundles that the EDT otherwise loads when it creates the first editor.
 * The initialize section runs first and alone, because the EDT initializes the same classes when it creates an editor,
 * and a second initialization order can reach a class initialization deadlock.
 * The other steps only load classes or read files, so they run in parallel.
 */
@RequiresBackgroundThread
internal suspend fun warmUpEditorClasses(listenerPoint: ExtensionPointName<EditorFactoryListener>) {
  val classLoader = EditorWarmUp::class.java.classLoader
  warmUpClasses(EDITOR_CLASSES.initialize, initialize = true) { classLoader }
  coroutineScope {
    launch { warmUpClasses(EDITOR_CLASSES.loadOnly, initialize = false) { classLoader } }
    launch { warmUpInspectionWidgetBundles() }
    launch { warmUpExtensionClasses(listenerPoint, "editor factory listener") }
    launch { warmUpExtensionClasses(INSPECTION_WIDGET_ACTION_PROVIDER_EP, "inspection widget action provider") }
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
 * Loads registered extension classes through their plugin class loaders.
 * The extension instances stay uncreated.
 */
private suspend fun <T : Any> warmUpExtensionClasses(extensionPoint: ExtensionPointName<T>, extensionKind: String) {
  for (extension in extensionPoint.filterableLazySequence()) {
    currentCoroutineContext().ensureActive()
    try {
      extension.implementationClass?.declaredConstructors
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot prepare $extensionKind class: ${extension.implementationClassName}", e)
    }
  }
}
