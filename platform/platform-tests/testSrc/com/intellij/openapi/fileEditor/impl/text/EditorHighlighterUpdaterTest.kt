// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.includeClassFile
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.lang.ref.Reference
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class EditorHighlighterUpdaterTest {
  private val projectFixture: TestFixture<Project> = projectFixture()
  private val directoryFixture: TestFixture<Path> = tempPathFixture()

  @ParameterizedTest
  @CsvSource("false, false", "true, false", "true, true")
  @SystemProperty(propertyKey = "intellij.indexes.skip.reload.on.plugin.load.unload", propertyValue = "true")
  fun `plugin unload releases the highlighter with an open editor`(
    backgroundNotification: Boolean,
    loadingEditor: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    if (backgroundNotification) assumeTrue(useBackgroundWriteAction)
    val project = projectFixture.get()
    val application = ApplicationManager.getApplication()
    val pluginsDir = directoryFixture.get()
    val descriptor = withContext(Dispatchers.IO) {
      loadDescriptorInTest(plugin {
        dependsIntellijModulesLang()
        extensions("""<fileType name="Updater unload test" implementationClass="${UnloadTestFileType::class.java.name}"
                                extensions="updaterUnload"/>""")
        includeClassFile<UnloadTestFileType>()
        includeClassFile<UnloadTestHighlighter>()
      }, pluginsDir)
    }
    val file = object : LightVirtualFile("highlighter.updaterUnload", "text") {
      override fun getFileType(): FileType = FileTypeManager.getInstance().getFileTypeByFileName(name)
    }
    val factory = EditorFactory.getInstance()
    val editor = withContext(Dispatchers.EDT) {
      factory.createEditor(factory.createDocument("text"), project) as EditorEx
    }
    val editorScope = childScope("unloading editor highlighter")
    try {
      withContext(Dispatchers.EDT) {
        assertTrue(DynamicPlugins.loadPlugin(descriptor))
        assertSame(descriptor.pluginClassLoader, file.fileType.javaClass.classLoader)
        editor.highlighter = descriptor.pluginClassLoader!!.loadClass(UnloadTestHighlighter::class.java.name)
          .getDeclaredConstructor().newInstance() as EditorHighlighter
      }
      val classloaderWatcher = GCWatcher.tracking(descriptor.pluginClassLoader)
      val replaced = CompletableDeferred<Unit>()
      val bus = if (backgroundNotification) {
        MessageBusFactory.newMessageBus(project as MessageBusOwner).also { Disposer.register(disposable, it) }
      }
      else {
        project.messageBus
      }
      val loader = if (loadingEditor) {
        withContext(Dispatchers.EDT) {
          createAsyncEditorLoader(TextEditorProvider.getInstance(), project, file, editorScope)
        }
      }
      else null
      val updater = object : EditorHighlighterUpdater(project, disposable, bus.connect(disposable), editor, file, loader) {
        override fun setupHighlighter(highlighter: EditorHighlighter) {
          application.assertIsDispatchThread()
          super.setupHighlighter(highlighter)
          replaced.complete(Unit)
        }
      }
      if (backgroundNotification) {
        backgroundWriteAction {
          assertFalse(application.isDispatchThread)
          bus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(descriptor, false)
          assertFalse(replaced.isCompleted)
          assertSame(descriptor.pluginClassLoader, editor.highlighter.javaClass.classLoader)
        }
        replaced.await()
      }
      withContext(Dispatchers.EDT) {
        assertTrue(DynamicPlugins.unloadPlugin(descriptor))
      }
      replaced.await()
      classloaderWatcher.ensureCollected()
      Reference.reachabilityFence(updater)
      withContext(Dispatchers.EDT) {
        assertFalse(editor.isDisposed)
        assertSame(EmptyEditorHighlighter::class.java.classLoader, editor.highlighter.javaClass.classLoader)
      }
    }
    finally {
      editorScope.cancel()
      withContext(Dispatchers.EDT) {
        factory.releaseEditor(editor)
        if (descriptor.pluginClassLoader != null) {
          DynamicPlugins.unloadPlugin(descriptor)
        }
      }
      editorScope.coroutineContext.job.join()
    }
  }

  @Test
  fun `file type removal discards an obsolete highlighter`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    assumeTrue(useBackgroundWriteAction)
    val project = projectFixture.get()
    val application = ApplicationManager.getApplication()
    val factory = EditorFactory.getInstance()
    val editor = withContext(Dispatchers.EDT) {
      factory.createEditor(factory.createDocument("text"), project) as EditorEx
    }
    val bus = MessageBusFactory.newMessageBus(project as MessageBusOwner)
    Disposer.register(disposable, bus)
    val removed = AtomicBoolean()
    val computed = CompletableDeferred<Unit>()
    val installed = CompletableDeferred<Unit>()
    val stale = EmptyEditorHighlighter()
    val replacement = EmptyEditorHighlighter()
    val updater = object : EditorHighlighterUpdater(project, disposable, bus.connect(disposable), editor, null, null) {
      override fun createHighlighter(forceEmpty: Boolean): EditorHighlighter {
        if (removed.get()) return replacement
        computed.complete(Unit)
        return stale
      }

      override fun setupHighlighter(highlighter: EditorHighlighter) {
        application.assertIsDispatchThread()
        assertSame(replacement, highlighter)
        super.setupHighlighter(highlighter)
        installed.complete(Unit)
      }
    }
    val entered = CompletableDeferred<Unit>()
    val proceed = CountDownLatch(1)
    val gate = async(Dispatchers.UI) {
      entered.complete(Unit)
      check(proceed.await(10, TimeUnit.SECONDS))
    }
    try {
      entered.await()
      updater.updateHighlighters()
      computed.await()
      backgroundWriteAction {
        removed.set(true)
        bus.syncPublisher(FileTypeManager.TOPIC).fileTypesChanged(
          FileTypeEvent(FileTypeManager.getInstance(), null, PlainTextFileType.INSTANCE, null),
        )
        assertFalse(installed.isCompleted)
      }
      proceed.countDown()
      gate.await()
      installed.await()
    }
    finally {
      proceed.countDown()
      gate.join()
      withContext(Dispatchers.EDT) { factory.releaseEditor(editor) }
    }
  }
}

internal class UnloadTestFileType : FileType {
  override fun getName(): String = "Updater unload test"
  override fun getDescription(): String = "Updater unload test"
  override fun getDefaultExtension(): String = "updaterUnload"
  override fun getIcon(): Icon? = null
  override fun isBinary(): Boolean = false
}

internal class UnloadTestHighlighter : EmptyEditorHighlighter()
