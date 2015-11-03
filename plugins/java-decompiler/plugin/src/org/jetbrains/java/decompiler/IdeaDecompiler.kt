/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler

import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.jetbrains.java.decompiler.main.extern.IResultSaver
import java.io.File
import java.util.*
import java.util.jar.Manifest

class IdeaDecompiler : ClassFileDecompilers.Light() {
  companion object {
    val BANNER = "//\n// Source code recreated from a .class file by IntelliJ IDEA\n// (powered by Fernflower decompiler)\n//\n\n"

    private val LEGAL_NOTICE_KEY = "decompiler.legal.notice.accepted"

    private fun getOptions(): Map<String, Any> {
      val project = DefaultProjectFactory.getInstance().defaultProject
      val options = CodeStyleSettingsManager.getInstance(project).currentSettings.getIndentOptions(JavaFileType.INSTANCE)
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
          IFernflowerPreferences.UNIT_TEST_MODE to if (ApplicationManager.getApplication().isUnitTestMode) "1" else "0")
    }
  }

  private val myLogger = lazy { IdeaLogger() }
  private val myOptions = lazy { getOptions() }
  private val myProgress = ContainerUtil.newConcurrentMap<VirtualFile, ProgressIndicator>()
  private var myLegalNoticeAccepted = false

  init {
    val app = ApplicationManager.getApplication()
    myLegalNoticeAccepted = app.isUnitTestMode || PropertiesComponent.getInstance().isValueSet(LEGAL_NOTICE_KEY)
    if (!myLegalNoticeAccepted) {
      app.messageBus.connect(app).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerAdapter() {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          if (file.fileType === StdFileTypes.CLASS) {
            val editor = source.getSelectedEditor(file)
            if (editor is TextEditor) {
              val text = editor.editor.document.immutableCharSequence
              if (StringUtil.startsWith(text, BANNER)) {
                showLegalNotice(source.project, file)
              }
            }
          }
        }
      })
    }
  }

  private fun showLegalNotice(project: Project, file: VirtualFile) {
    if (!myLegalNoticeAccepted) {
      ApplicationManager.getApplication().invokeLater({
        if (!myLegalNoticeAccepted) {
          object : LegalNoticeDialog(project) {
            override fun accepted() {
              PropertiesComponent.getInstance().setValue(LEGAL_NOTICE_KEY, true)
              myLegalNoticeAccepted = true
            }

            override fun declined() {
              PluginManagerCore.disablePlugin("org.jetbrains.java.decompiler")
              ApplicationManagerEx.getApplicationEx().restart(true)
            }

            override fun canceled() {
              FileEditorManager.getInstance(project).closeFile(file)
            }
          }.show()
        }
      }, ModalityState.NON_MODAL)
    }
  }

  override fun accepts(file: VirtualFile): Boolean = true

  override fun getText(file: VirtualFile): CharSequence {
    if ("package-info.class" == file.name) {
      return ClsFileImpl.decompile(file)
    }

    val indicator = ProgressManager.getInstance().progressIndicator
    if (indicator != null) myProgress.put(file, indicator)

    try {
      val files = linkedMapOf(file.path to file)
      val mask = "${file.nameWithoutExtension}$"
      file.parent.children.forEach { child ->
        if (child.nameWithoutExtension.startsWith(mask) && child.fileType === StdFileTypes.CLASS) {
          files.put(FileUtil.toSystemIndependentName(child.path), child)
        }
      }

      val options = HashMap(myOptions.value)
      if (Registry.`is`("decompiler.use.line.mapping")) {
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1")
        options.put(IFernflowerPreferences.USE_DEBUG_LINE_NUMBERS, "0")
      }
      else if (Registry.`is`("decompiler.use.line.table")) {
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "0")
        options.put(IFernflowerPreferences.USE_DEBUG_LINE_NUMBERS, "1")
      }
      else {
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "0")
        options.put(IFernflowerPreferences.USE_DEBUG_LINE_NUMBERS, "0")
      }
      if (Registry.`is`("decompiler.dump.original.lines")) {
        options.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1")
      }

      val provider = MyBytecodeProvider(files)
      val saver = MyResultSaver()
      val decompiler = BaseDecompiler(provider, saver, options, myLogger.value)
      files.keys.forEach { path -> decompiler.addSpace(File(path), true) }
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
      if (ApplicationManager.getApplication().isUnitTestMode) {
        val error = AssertionError(file.url)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        (error as java.lang.Throwable).initCause(e)
        throw error
      }
      else {
        throw ClassFileDecompilers.Light.CannotDecompileException(e)
      }
    }
    finally {
      myProgress.remove(file)
    }
  }

  @TestOnly
  fun getProgress(file: VirtualFile): ProgressIndicator? = myProgress[file]

  private class MyBytecodeProvider(private val files: Map<String, VirtualFile>) : IBytecodeProvider {
    override fun getBytecode(externalPath: String, internalPath: String?): ByteArray {
      val path = FileUtil.toSystemIndependentName(externalPath)
      val file = files[path]
      assert(file != null) { path + " not in " + files.keys }
      return file!!.contentsToByteArray()
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
    override fun bytecodeToSource(line: Int): Int {
      for (i in mapping.indices step 2) {
        if (mapping[i] == line) {
          return mapping[i + 1]
        }
      }
      return -1
    }

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