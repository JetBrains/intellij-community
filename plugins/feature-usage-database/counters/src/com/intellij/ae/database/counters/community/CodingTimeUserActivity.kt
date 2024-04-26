// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community

import com.intellij.ae.database.core.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.core.createMap
import com.intellij.ae.database.core.runUpdateEvent
import com.intellij.ae.database.core.utils.InstantUtils
import com.intellij.lang.Language
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.validOrNull
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.validOrNull
import com.intellij.util.io.DigestUtil
import org.jetbrains.sqlite.ObjectBinderFactory
import java.math.BigInteger
import java.time.Instant
import java.util.*

/**
 * Coding activity. Stores language of the file and file hash
 */
object CodingTimeUserActivity : WritableDatabaseBackedTimeSpanUserActivity() {
  override val canBeStale = true
  override val id = "editor.changing"

  suspend fun write(editorId: String, language: Language, file: VirtualFile) {
    val extra = mapOf("lang" to language.id, "fileHash" to md5(file.presentableUrl))
    submitPeriodic(editorId, extra)
  }

  /**
   * How many files were edited in the period of [from]..[until]
   */
  suspend fun getFilesEdited(from: Instant, until: Instant): Int? {
    return getDatabase().execute { database ->
      val filesEditedStatement = database
          .prepareStatement("SELECT COUNT(DISTINCT json_extract(extra, '\$.fileHash')) FROM timespanUserActivity\n" +
                            "WHERE activity_id = '$id'\n" +
                            "AND datetime(started_at) >= datetime(?)\n" +
                            "AND datetime(ended_at) <= datetime(?)", ObjectBinderFactory.create2<String, String>())

      filesEditedStatement.binder.bind(InstantUtils.formatForDatabase(from), InstantUtils.formatForDatabase(until))
      filesEditedStatement.selectInt() ?: 0
    }
  }

  /**
   * How much files per language were edited / map of  (language name -> length in seconds)
   */
  suspend fun getByLanguageStat(from: Instant, until: Instant): Map<LanguageWrapper, Int>? {
    return getDatabase().execute { database ->
      val byLanguageStatStatement = database
        .prepareStatement("SELECT json_extract(extra, '\$.lang') as lang, SUM(strftime('%s', ended_at) - strftime('%s', started_at)) FROM timespanUserActivity\n" +
                          "WHERE activity_id = '$id'\n" +
                          "AND datetime(started_at) >= datetime(?)\n" +
                          "AND datetime(ended_at) <= datetime(?)\n" +
                          "GROUP BY lang", ObjectBinderFactory.create2<String, String>())
      byLanguageStatStatement.binder.bind(InstantUtils.formatForDatabase(from), InstantUtils.formatForDatabase(until))
      val res = byLanguageStatStatement.executeQuery()

      createMap(
        { it.getString(0) }, { it.getInt(1) },
        { k0, v0 -> k0 != null && v0 != 0 },
        { LanguageWrapper(Language.findLanguageByID(it!!), it) }, { it },
        { _, _ ->  true },
        res
      )
    }
  }

  private fun md5(buffer: String): String {
    val md5 = DigestUtil.md5()
    md5.update(buffer.toByteArray(Charsets.UTF_8))
    return BigInteger(md5.digest()).abs().toString(16)
  }
}

/**
 * Keeps language ID in case Language is not found
 */
data class LanguageWrapper(
  val language: Language?,
  val id: String
)

@Service(Service.Level.PROJECT)
private class DocumentHolder {
  companion object {
    fun getInstance(project: Project) = project.getService(DocumentHolder::class.java)
  }

  private val urls = mutableSetOf<String>()

  fun submit(vf: VirtualFile) {
    urls.add(vf.url)
  }

  fun remove(vf: VirtualFile) {
    urls.remove(vf.url)
  }

  fun isAllowed(vf: VirtualFile): Boolean {
    return urls.contains(vf.url)
  }
}

internal class CodingTimeUserActivityEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor as EditorEx
    val project = editor.project ?: return
    val editorId = UUID.randomUUID().toString()

    val disposable = Disposer.newDisposable()
    EditorUtil.disposeWithEditor(editor, disposable)

    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val vf = editor.virtualFile?.validOrNull() ?: return
        if (DocumentHolder.getInstance(project).isAllowed(vf)) {
          FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(CodingTimeUserActivity) {
            val psiFile = readAction {
              if (vf.isValid) {
                PsiManagerEx.getInstance(project).findFile(vf)?.validOrNull()
              }
              else {
                null
              }
            } ?: return@runUpdateEvent
            it.write(editorId, psiFile.language, vf)
          }
        }
      }
    }, disposable)
  }
}

internal class CodingTimeUserActivityCommandListener : CommandListener {
  override fun commandStarted(event: CommandEvent) {
    val doc = event.commandGroupId as? Document
    if (doc != null) {
      val vf = FileDocumentManager.getInstance().getFile(doc)?.validOrNull()
      val project = event.project
      if (vf != null && project != null) {
        DocumentHolder.getInstance(project).submit(vf)
      }
    }
  }

  override fun commandFinished(event: CommandEvent) {
    val doc = event.commandGroupId as? Document
    if (doc != null) {
      val vf = FileDocumentManager.getInstance().getFile(doc)?.validOrNull()
      val project = event.project
      if (vf != null && project != null) {
        DocumentHolder.getInstance(project).remove(vf)
      }
    }
  }
}