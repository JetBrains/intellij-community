// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.KeyedFactoryEPBean
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.fileTypes.impl.AbstractFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.messages.SimpleMessageBusConnection
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class EditorHighlighterUpdater(
  @JvmField protected val project: Project,
  parentDisposable: Disposable,
  connection: SimpleMessageBusConnection,
  @JvmField protected val editor: EditorEx,
  private val file: VirtualFile?,
  private val asyncLoader: AsyncEditorLoader?,
) {
  constructor(
    project: Project,
    parentDisposable: Disposable,
    editor: EditorEx,
    file: VirtualFile?,
  ) : this(
    project = project,
    parentDisposable = parentDisposable,
    connection = project.messageBus.connect(parentDisposable),
    editor = editor,
    file = file,
    asyncLoader = null,
  )

  init {
    connection.subscribe(FileTypeManager.TOPIC, MyFileTypeListener())
    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        updateHighlighters()
      }

      override fun exitDumbMode() {
        updateHighlighters()
      }
    })

    updateHighlightersOnExtensionChange(parentDisposable, LanguageSyntaxHighlighters.EP_NAME)
    updateHighlightersOnExtensionChange(parentDisposable, SyntaxHighlighterLanguageFactory.EP_NAME)
    updateHighlightersOnExtensionChange(parentDisposable, FileTypeEditorHighlighterProviders.EP_NAME)

    SyntaxHighlighter.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<KeyedFactoryEPBean> {
      override fun extensionAdded(extension: KeyedFactoryEPBean, pluginDescriptor: PluginDescriptor) {
        checkUpdateHighlighters(key = extension.key, updateSynchronously = false)
      }

      override fun extensionRemoved(extension: KeyedFactoryEPBean, pluginDescriptor: PluginDescriptor) {
        checkUpdateHighlighters(key = extension.key, updateSynchronously = true)
      }
    }, parentDisposable)

    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        val loadedPluginDescriptor = getPlugin(pluginDescriptor.pluginId)
        val pluginClassLoader = loadedPluginDescriptor?.pluginClassLoader
        if (this@EditorHighlighterUpdater.file != null && pluginClassLoader is PluginAwareClassLoader) {
          val fileType = file.fileType
          if (fileType.javaClass.classLoader === pluginClassLoader ||
              (fileType is LanguageFileType && fileType.javaClass.classLoader === pluginClassLoader)) {
            setupHighlighter(createHighlighter(true))
          }
        }
      }
    })
  }

  private fun <T> updateHighlightersOnExtensionChange(parentDisposable: Disposable, epName: ExtensionPointName<KeyedLazyInstance<T>>) {
    epName.addExtensionPointListener(object : ExtensionPointListener<KeyedLazyInstance<T>> {
      override fun extensionAdded(extension: KeyedLazyInstance<T>, pluginDescriptor: PluginDescriptor) {
        checkUpdateHighlighters(extension.key, false)
      }

      override fun extensionRemoved(extension: KeyedLazyInstance<T>, pluginDescriptor: PluginDescriptor) {
        checkUpdateHighlighters(extension.key, true)
      }
    }, parentDisposable)
  }

  private fun checkUpdateHighlighters(key: String, updateSynchronously: Boolean) {
    if (file != null) {
      val fileType = file.fileType
      val needUpdate = (fileType.name == key || (fileType is LanguageFileType && fileType.language.id == key))
      if (!needUpdate) {
        return
      }
    }

    if (updateSynchronously && ApplicationManager.getApplication().isDispatchThread) {
      updateHighlightersSynchronously()
    }
    else {
      updateHighlighters()
    }
  }

  /**
   * Updates editors' highlighters. This should be done when the opened file changes its file type.
   */
  fun updateHighlighters() {
    if (project.isDisposed || editor.isDisposed) {
      return
    }

    if (asyncLoader != null && !asyncLoader.isLoaded()) {
      return
    }

    ReadAction
      .nonBlocking<EditorHighlighter> { createHighlighter(forceEmpty = false) }
      .expireWith(project)
      .expireWhen { (file != null && !file.isValid) || editor.isDisposed }
      .coalesceBy(EditorHighlighterUpdater::class.java, editor)
      .finishOnUiThread(ModalityState.any(), ::setupHighlighter)
      .submit(NonUrgentExecutor.getInstance())
  }

  protected open fun createHighlighter(forceEmpty: Boolean): EditorHighlighter {
    val highlighter = if (file != null && !forceEmpty) {
      EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
    }
    else {
      EmptyEditorHighlighter(EditorColorsManager.getInstance().globalScheme, HighlighterColors.TEXT)
    }
    highlighter.setText(editor.document.immutableCharSequence)
    return highlighter
  }

  protected open fun setupHighlighter(highlighter: EditorHighlighter) {
    editor.highlighter = highlighter
  }

  private fun updateHighlightersSynchronously() {
    if (!project.isDisposed && !editor.isDisposed && (asyncLoader == null || asyncLoader.isLoaded())) {
      setupHighlighter(createHighlighter(forceEmpty = false))
    }
  }

  /**
   * Listen to changes of file types. When the type of the file changes, we need to also change highlighter.
   */
  private inner class MyFileTypeListener : FileTypeListener {
    override fun fileTypesChanged(event: FileTypeEvent) {
      ThreadingAssertions.assertEventDispatchThread()
      // File can be invalid after file type changing. The editor should be removed by the FileEditorManager if it's invalid.
      val type = event.removedFileType
      if (type != null && type !is AbstractFileType) {
        // Plugin is being unloaded, so we need to release plugin classes immediately
        updateHighlightersSynchronously()
      }
      else {
        updateHighlighters()
      }
    }
  }
}
