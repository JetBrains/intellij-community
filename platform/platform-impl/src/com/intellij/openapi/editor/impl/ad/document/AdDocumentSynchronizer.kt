// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import andel.operation.Operation
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.ad.AD_DISPATCHER
import com.intellij.openapi.editor.impl.ad.ThreadLocalRhizomeDB
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.ui.EDT
import fleet.kernel.awaitCommitted
import fleet.kernel.change
import fleet.kernel.shared
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
@Service(Level.APP)
internal class AdDocumentSynchronizer(private val coroutineScope: CoroutineScope): Disposable.Default {

  companion object {
    fun getInstance(): AdDocumentSynchronizer = service()
  }

  init {
    // TODO
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
      object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          if (EDT.isCurrentThreadEdt()) {
            val document = event.document
            val file = FileDocumentManager.getInstance().getFile(document)
            if (file is VirtualFileWithId && (document is DocumentImpl) && document.isWriteThreadOnly) {
              val entityChange = coroutineScope.async(AD_DISPATCHER) {
                val entity = DocumentEntityManager.getInstance().getDocEntity(document)
                if (entity != null) {
                  val operation = operation(event)
                  change {
                    shared { // shared to mutate shared document components (e.g., AdMarkupModel)
                      entity.mutate(this, OpenMap.empty()) {
                        edit(operation)
                      }
                    }
                  }
                  awaitCommitted()
                  true
                } else {
                  false
                }
              }
              // TODO: cannot replace with runWithModalProgressBlocking because pumping events ruins the models
              val changed = runBlocking { entityChange.await() }
              if (changed) {
                ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
              }
            }
          }
        }
      },
      this,
    )
  }

  private fun operation(event: DocumentEvent): Operation {
    val oldFragment = event.oldFragment.toString()
    val newFragment = event.newFragment.toString()
    val lengthBefore = event.document.textLength - newFragment.length + oldFragment.length
    return Operation.Companion.replaceAt(
      offset = event.offset.toLong(),
      oldText = oldFragment,
      newText = newFragment,
      totalLength = lengthBefore.toLong(),
    )
  }
}
