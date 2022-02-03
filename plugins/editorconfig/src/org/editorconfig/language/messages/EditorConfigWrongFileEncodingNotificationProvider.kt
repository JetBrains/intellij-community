// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.messages

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction
import com.intellij.openapi.vfs.encoding.EncodingUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ArrayUtil
import org.editorconfig.language.filetype.EditorConfigFileConstants
import java.util.*

class EditorConfigWrongFileEncodingNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {
  override fun getKey() = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    fileEditor as? TextEditor ?: return null
    val editor = fileEditor.editor
    if (editor.getUserData(HIDDEN_KEY) != null) return null
    if (PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) return null
    if (file.extension != EditorConfigFileConstants.FILE_EXTENSION) return null
    if (file.charset == Charsets.UTF_8) return null
    return buildPanel(project, editor, file)
  }

  private fun buildPanel(project: Project, editor: Editor, file: VirtualFile): EditorNotificationPanel? {
    val result = EditorNotificationPanel(editor, null, null)
    result.text(EditorConfigBundle.get("notification.encoding.message"))

    val convert = EditorConfigBundle["notification.action.convert"]
    result.createActionLabel(convert) {
      val text = editor.document.text
      val isSafeToConvert = isSafeToConvertTo(file, text)
      val isSafeToReload = isSafeToReloadIn(file, text)
      ChangeFileEncodingAction.changeTo(project, editor.document, editor, file, Charsets.UTF_8, isSafeToConvert, isSafeToReload)
    }

    val hide = EditorConfigBundle["notification.action.hide.once"]
    result.createActionLabel(hide) {
      editor.putUserData<Boolean>(HIDDEN_KEY, true)
      update(file, project)
    }

    val hideForever = EditorConfigBundle["notification.action.hide.forever"]
    result.createActionLabel(hideForever) {
      PropertiesComponent.getInstance().setValue(DISABLE_KEY, true)
      update(file, project)
    }

    return result
  }

  private fun update(file: VirtualFile, project: Project) = EditorNotifications.getInstance(project).updateNotifications(file)
  private fun isSafeToReloadIn(file: VirtualFile, text: CharSequence): EncodingUtil.Magic8 {
    val bytes = file.contentsToByteArray()
    // file has BOM but the charset hasn't
    val bom = file.bom
    if (bom != null && !CharsetToolkit.canHaveBom(Charsets.UTF_8, bom)) return EncodingUtil.Magic8.NO_WAY

    // the charset has mandatory BOM (e.g. UTF-xx) but the file hasn't or has wrong
    val mandatoryBom = CharsetToolkit.getMandatoryBom(Charsets.UTF_8)
    if (mandatoryBom != null && !ArrayUtil.startsWith(bytes, mandatoryBom)) return EncodingUtil.Magic8.NO_WAY

    val loaded = LoadTextUtil.getTextByBinaryPresentation(bytes, Charsets.UTF_8).toString()

    var bytesToSave: ByteArray = try {
      val separator = FileDocumentManager.getInstance().getLineSeparator(file, null)
      StringUtil.convertLineSeparators(loaded, separator).toByteArray(Charsets.UTF_8)
    }
    catch (e: UnsupportedOperationException) {
      return EncodingUtil.Magic8.NO_WAY
    }
    catch (e: NullPointerException) {
      return EncodingUtil.Magic8.NO_WAY
    }
    // turned out some crazy charsets have incorrectly implemented .newEncoder() returning null
    if (bom != null && !ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave) // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    return if (!Arrays.equals(bytesToSave, bytes)) EncodingUtil.Magic8.NO_WAY
    else if (StringUtil.equals(loaded, text)) EncodingUtil.Magic8.ABSOLUTELY
    else EncodingUtil.Magic8.WELL_IF_YOU_INSIST
  }

  private fun isSafeToConvertTo(file: VirtualFile, text: CharSequence) = try {
    val bytesOnDisk = file.contentsToByteArray()
    val lineSeparator = FileDocumentManager.getInstance().getLineSeparator(file, null)
    val textToSave = if (lineSeparator == "\n") text else StringUtilRt.convertLineSeparators(text, lineSeparator)
    val saved = LoadTextUtil.chooseMostlyHarmlessCharset(file.charset, Charsets.UTF_8, textToSave.toString()).second
    val textLoadedBack = LoadTextUtil.getTextByBinaryPresentation(saved, Charsets.UTF_8)
    when {
      !StringUtil.equals(text, textLoadedBack) -> EncodingUtil.Magic8.NO_WAY
      Arrays.equals(saved, bytesOnDisk) -> EncodingUtil.Magic8.ABSOLUTELY
      else -> EncodingUtil.Magic8.WELL_IF_YOU_INSIST
    }
  }
  catch (e: UnsupportedOperationException) { // unsupported encoding
    EncodingUtil.Magic8.NO_WAY
  }

  private companion object {
    private val KEY = Key.create<EditorNotificationPanel>("editorconfig.wrong.encoding.notification")
    private val HIDDEN_KEY = Key.create<Boolean>("editorconfig.wrong.encoding.notification.hidden")
    private const val DISABLE_KEY = "editorconfig.wrong.encoding.notification.disabled"
  }
}
