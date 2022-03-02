// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.application.options.CodeStyle
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.ui.components.LegalNoticeDialog
import com.intellij.util.FileContentUtilCore
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.ClassFormatException
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.jetbrains.java.decompiler.main.extern.IResultSaver
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.jar.Manifest

class IdeaDecompiler : ClassFileDecompilers.Light() {
  companion object {
    const val BANNER: String = "//\n// Source code recreated from a .class file by IntelliJ IDEA\n// (powered by FernFlower decompiler)\n//\n\n"

    private const val LEGAL_NOTICE_KEY = "decompiler.legal.notice.accepted"

    private const val POSTPONE_EXIT_CODE = DialogWrapper.CANCEL_EXIT_CODE
    private const val DECLINE_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE

    private val TASK_KEY: Key<Future<CharSequence>> = Key.create("java.decompiler.optimistic.task")

    private fun getOptions(): Map<String, Any> {
      val options = CodeStyle.getDefaultSettings().getIndentOptions(JavaFileType.INSTANCE)
      val indent = StringUtil.repeat(" ", options.INDENT_SIZE)
      return mapOf(
        IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR to "0",
        IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES to "1",
        IFernflowerPreferences.REMOVE_SYNTHETIC to "1",
        IFernflowerPreferences.REMOVE_BRIDGE to "1",
        IFernflowerPreferences.NEW_LINE_SEPARATOR to "1",
        IFernflowerPreferences.BANNER to BANNER,
        IFernflowerPreferences.MAX_PROCESSING_METHOD to 60,
        IFernflowerPreferences.INDENT_STRING to indent,
        IFernflowerPreferences.IGNORE_INVALID_BYTECODE to "1",
        IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES to "1",
        IFernflowerPreferences.UNIT_TEST_MODE to if (ApplicationManager.getApplication().isUnitTestMode) "1" else "0")
    }

    private fun canWork(): Boolean =
      ApplicationManager.getApplication().isUnitTestMode || PropertiesComponent.getInstance().isValueSet(LEGAL_NOTICE_KEY)
  }

  class LegalBurden : FileEditorManagerListener.Before {
    private var myShowNotice = !canWork()

    override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
      if (myShowNotice && file.fileType === JavaClassFileType.INSTANCE) {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Light::class.java)
        if (decompiler is IdeaDecompiler) {
          TASK_KEY.set(file, ApplicationManager.getApplication().executeOnPooledThread(Callable { decompiler.decompile(file) }))

          val title = IdeaDecompilerBundle.message("legal.notice.title", StringUtil.last(file.path, 40, true))
          val message = IdeaDecompilerBundle.message("legal.notice.text")
          val result = LegalNoticeDialog.build(title, message)
            .withCancelText(IdeaDecompilerBundle.message("legal.notice.action.postpone"))
            .withCustomAction(IdeaDecompilerBundle.message("legal.notice.action.reject"), DECLINE_EXIT_CODE)
            .show()
          when (result) {
            DialogWrapper.OK_EXIT_CODE -> {
              myShowNotice = false
              PropertiesComponent.getInstance().setValue(LEGAL_NOTICE_KEY, true)
              ApplicationManager.getApplication().invokeLater { FileContentUtilCore.reparseFiles(file) }
            }

            DECLINE_EXIT_CODE -> {
              myShowNotice = false
              TASK_KEY.set(file, null)

              val id = PluginId.getId("org.jetbrains.java.decompiler")
              PluginManagerCore.disablePlugin(id)

              val plugin = PluginManagerCore.getPlugin(id)
              if (plugin is IdeaPluginDescriptorImpl && DynamicPlugins.allowLoadUnloadWithoutRestart(plugin)) {
                ApplicationManager.getApplication().invokeLater {
                  DynamicPlugins.unloadPlugin(plugin, DynamicPlugins.UnloadPluginOptions(save = false))
                }
              }
            }

            POSTPONE_EXIT_CODE -> {
              TASK_KEY.set(file, null)
            }
          }
        }
      }
    }
  }

  private val myLogger = lazy { IdeaLogger() }
  private val myOptions = lazy { getOptions() }

  override fun accepts(file: VirtualFile): Boolean = true

  override fun getText(file: VirtualFile): CharSequence =
    if (canWork()) TASK_KEY.pop(file)?.get() ?: decompile(file)
    else ClsFileImpl.decompile(file)

  private fun decompile(file: VirtualFile): CharSequence {
    val indicator = ProgressManager.getInstance().progressIndicator
    if (indicator != null) {
      indicator.text = IdeaDecompilerBundle.message("decompiling.progress", file.name)
    }

    try {
      val mask = "${file.nameWithoutExtension}$"
      val files = listOf(file) + file.parent.children.filter { it.name.startsWith(mask) && it.fileType === JavaClassFileType.INSTANCE }

      val options = HashMap(myOptions.value)
      if (Registry.`is`("decompiler.use.line.mapping")) {
        options[IFernflowerPreferences.BYTECODE_SOURCE_MAPPING] = "1"
      }
      if (Registry.`is`("decompiler.dump.original.lines")) {
        options[IFernflowerPreferences.DUMP_ORIGINAL_LINES] = "1"
      }

      val provider = MyBytecodeProvider(files)
      val saver = MyResultSaver()
      val decompiler = BaseDecompiler(provider, saver, options, myLogger.value)
      files.forEach { decompiler.addSource(File(it.path)) }
      decompiler.decompileContext()

      val mapping = saver.myMapping
      if (mapping != null) {
        file.putUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY, LineNumbersMapping.ArrayBasedMapping(mapping))
      }

      return saver.myResult
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      when {
        e is IdeaLogger.InternalException && e.cause is IOException -> {
          Logger.getInstance(IdeaDecompiler::class.java).warn(file.url, e)
          return Strings.EMPTY_CHAR_SEQUENCE
        }
        ApplicationManager.getApplication().isUnitTestMode && e !is ClassFormatException -> throw AssertionError(file.url, e)
        else -> throw CannotDecompileException(file.url, e)
      }
    }
  }

  private class MyBytecodeProvider(files: List<VirtualFile>) : IBytecodeProvider {
    private val pathMap = files.associateBy { File(it.path).absolutePath }

    override fun getBytecode(externalPath: String, internalPath: String?): ByteArray =
      pathMap[externalPath]?.contentsToByteArray(false) ?: throw AssertionError(externalPath + " not in " + pathMap.keys)
  }

  private class MyResultSaver : IResultSaver {
    var myResult = ""
    var myMapping: IntArray? = null

    override fun saveClassFile(path: String, qualifiedName: String, entryName: String, content: String, mapping: IntArray?) {
      if (myResult.isEmpty()) {
        myResult = content
        myMapping = mapping
      }
    }

    override fun saveFolder(path: String) { }

    override fun copyFile(source: String, path: String, entryName: String) { }

    override fun createArchive(path: String, archiveName: String, manifest: Manifest) { }

    override fun saveDirEntry(path: String, archiveName: String, entryName: String) { }

    override fun copyEntry(source: String, path: String, archiveName: String, entry: String) { }

    override fun saveClassEntry(path: String, archiveName: String, qualifiedName: String, entryName: String, content: String) { }

    override fun closeArchive(path: String, archiveName: String) { }
  }

  private fun <T> Key<T>.pop(holder: UserDataHolder): T? {
    val value = get(holder)
    if (value != null) set(holder, null)
    return value
  }
}
