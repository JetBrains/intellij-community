// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler

import com.intellij.application.options.CodeStyle
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiPackage
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.ui.components.LegalNoticeDialog
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.jetbrains.java.decompiler.main.extern.IResultSaver
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.jar.Manifest

class IdeaDecompiler : ClassFileDecompilers.Light() {
  companion object {
    const val BANNER = "//\n// Source code recreated from a .class file by IntelliJ IDEA\n// (powered by Fernflower decompiler)\n//\n\n"

    private const val LEGAL_NOTICE_KEY = "decompiler.legal.notice.accepted"

    private const val POSTPONE_EXIT_CODE = DialogWrapper.CANCEL_EXIT_CODE
    private const val DECLINE_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE

    private fun getOptions(): Map<String, Any> {
      val options = CodeStyle.getDefaultSettings().getIndentOptions(JavaFileType.INSTANCE)
      val indent = StringUtil.repeat(" ", options.INDENT_SIZE)
      return mapOf(
        IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR to "0",
        IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES to "1",
        IFernflowerPreferences.REMOVE_SYNTHETIC to "1",
        IFernflowerPreferences.REMOVE_BRIDGE to "1",
        IFernflowerPreferences.LITERALS_AS_IS to "1",
        IFernflowerPreferences.NEW_LINE_SEPARATOR to "1",
        IFernflowerPreferences.BANNER to BANNER,
        IFernflowerPreferences.MAX_PROCESSING_METHOD to 60,
        IFernflowerPreferences.INDENT_STRING to indent,
        IFernflowerPreferences.IGNORE_INVALID_BYTECODE to "1",
        IFernflowerPreferences.UNIT_TEST_MODE to if (ApplicationManager.getApplication().isUnitTestMode) "1" else "0")
    }
  }

  private val myLogger = lazy { IdeaLogger() }
  private val myOptions = lazy { getOptions() }
  private val myFutures = ContainerUtil.newConcurrentMap<VirtualFile, Future<CharSequence>>()
  @Volatile private var myLegalNoticeAccepted = false

  init {
    myLegalNoticeAccepted = ApplicationManager.getApplication().isUnitTestMode || PropertiesComponent.getInstance().isValueSet(LEGAL_NOTICE_KEY)
    if (!myLegalNoticeAccepted) {
      intercept()
    }
  }

  private fun intercept() {
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.connect(app)
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
      override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!myLegalNoticeAccepted && file.fileType === StdFileTypes.CLASS && ClassFileDecompilers.find(file) === this@IdeaDecompiler) {
          myFutures[file] = app.executeOnPooledThread(Callable<CharSequence> { decompile(file) })

          val title = IdeaDecompilerBundle.message("legal.notice.title", StringUtil.last(file.path, 40, true))
          val message = IdeaDecompilerBundle.message("legal.notice.text")
          val result = LegalNoticeDialog.build(title, message)
            .withCancelText(IdeaDecompilerBundle.message("legal.notice.action.postpone"))
            .withCustomAction(IdeaDecompilerBundle.message("legal.notice.action.reject"), DECLINE_EXIT_CODE)
            .show()
          when (result) {
            DialogWrapper.OK_EXIT_CODE -> {
              PropertiesComponent.getInstance().setValue(LEGAL_NOTICE_KEY, true)
              myLegalNoticeAccepted = true

              app.invokeLater {
                RefreshQueue.getInstance().processSingleEvent(
                  VFileContentChangeEvent(this@IdeaDecompiler, file, file.modificationStamp, -1, false))
              }

              connection.disconnect()
            }

            DECLINE_EXIT_CODE -> {
              myFutures.remove(file)?.cancel(true)
              PluginManagerCore.disablePlugin("org.jetbrains.java.decompiler")
              ApplicationManagerEx.getApplicationEx().restart(true)
            }

            POSTPONE_EXIT_CODE -> {
              myFutures.remove(file)?.cancel(true)
            }
          }
        }
      }
    })
  }

  override fun accepts(file: VirtualFile): Boolean = true

  override fun getText(file: VirtualFile): CharSequence =
      if (myLegalNoticeAccepted) myFutures.remove(file)?.get() ?: decompile(file)
      else ClsFileImpl.decompile(file)

  private fun decompile(file: VirtualFile): CharSequence {
    if (PsiPackage.PACKAGE_INFO_CLS_FILE == file.name || PsiJavaModule.MODULE_INFO_CLS_FILE == file.name) {
      return ClsFileImpl.decompile(file)
    }

    val indicator = ProgressManager.getInstance().progressIndicator
    if (indicator != null) {
      indicator.text = IdeaDecompilerBundle.message("decompiling.progress", file.name)
    }

    try {
      val mask = "${file.nameWithoutExtension}$"
      val files = mapOf(file.path to file) +
          file.parent.children.filter { it.nameWithoutExtension.startsWith(mask) && it.fileType === StdFileTypes.CLASS }.map { it.path to it }

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
      files.keys.forEach { path -> decompiler.addSource(File(path)) }
      decompiler.decompileContext()

      val mapping = saver.myMapping
      if (mapping != null) {
        file.putUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY, ExactMatchLineNumbersMapping(mapping))
      }

      return saver.myResult
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      if (e is IdeaLogger.InternalException && e.cause is IOException) {
        Logger.getInstance(IdeaDecompiler::class.java).warn(file.url, e)
        return ArrayUtil.EMPTY_CHAR_SEQUENCE
      }
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw AssertionError(file.url, e)
      }
      else {
        throw ClassFileDecompilers.Light.CannotDecompileException(e)
      }
    }
  }

  private class MyBytecodeProvider(private val files: Map<String, VirtualFile>) : IBytecodeProvider {
    override fun getBytecode(externalPath: String, internalPath: String?): ByteArray {
      val path = FileUtil.toSystemIndependentName(externalPath)
      val file = files[path] ?: throw AssertionError(path + " not in " + files.keys)
      return file.contentsToByteArray(false)
    }
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

  private class ExactMatchLineNumbersMapping(private val mapping: IntArray) : LineNumbersMapping {
    @Suppress("LoopToCallChain")
    override fun bytecodeToSource(line: Int): Int {
      for (i in mapping.indices step 2) {
        if (mapping[i] == line) {
          return mapping[i + 1]
        }
      }
      return -1
    }

    @Suppress("LoopToCallChain")
    override fun sourceToBytecode(line: Int): Int {
      for (i in mapping.indices step 2) {
        if (mapping[i + 1] == line) {
          return mapping[i]
        }
      }
      return -1
    }
  }
}