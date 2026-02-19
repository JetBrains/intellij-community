// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import andel.operation.Op
import andel.operation.Operation
import andel.operation.compose
import andel.operation.isNotIdentity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.util.ThreadLocalRhizomeDB
import com.intellij.platform.pasta.common.ChangeDocument
import com.intellij.platform.pasta.common.DocToEntityUpdate
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ui.EDT
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.SubscriptionEvent
import fleet.kernel.byUidOrNull
import fleet.kernel.change
import fleet.kernel.rebase.TransactionResult
import fleet.kernel.rebase.TransactionResultKey
import fleet.kernel.rebase.awaitCommitted
import fleet.kernel.transactor
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.Reference
import java.lang.ref.WeakReference


@Service(Level.APP)
class AdDocumentSynchronizer(private val coroutineScope: CoroutineScope): Disposable.Default {

  companion object {
    fun getInstance(): AdDocumentSynchronizer = service()
  }

  fun bindDocumentListener(document: DocumentEx): CoroutineScope {
    val debugName = document.toString()
    val cs = coroutineScope.childScope("doc->entity sync $debugName")
    coroutineScope.launch(AdTheManager.AD_DISPATCHER) {
      val entity = AdDocumentManager.getInstance().getDocEntity(document)
      checkNotNull(entity) { "entity $debugName not found" }
      @Suppress("DEPRECATION")
      document.addDocumentListener(
        DocToEntitySynchronizer(
          debugName,
          entity,
          WeakReference(document),
          cs,
        )
      )
    }
    return cs
  }

  private class DocToEntitySynchronizer(
    private val debugName: String,
    private val entity: DocumentEntity,
    private val documentRef: Reference<DocumentEx>,
    private val coroutineScope: CoroutineScope
  ) : PrioritizedDocumentListener {

    init {
      coroutineScope.launch {
        transactor().log.collect { e ->
          if (documentRef.get() == null) {
            cancel()
            return@collect
          }
          if (e is SubscriptionEvent.Next) {
            val change = e.change
            val tx = change.meta[TransactionResultKey]
            if (tx is TransactionResult.TransactionApplied) {
              val op = tx.tx.instructions.asSequence()
                .filter { it.name == ChangeDocument.instructionName }
                .map { it.instruction.get(ChangeDocument.serializer) }
                .filter {
                  asOf(change.dbAfter) {
                    val changedEntity = byUidOrNull<DocumentEntity>(it.documentId)
                    changedEntity?.eid == entity.eid
                  }
                }
                .filter { !it.docToDb }
                .map { it.operation }
                .toList()
                .compose()
              if (op.isNotIdentity()) {
                entityToDocumentImplChange(op)
              }
            }
          }
        }
      }
    }

    override fun getPriority(): Int = Int.MIN_VALUE + 1

    override fun documentChanged(event: DocumentEvent) {
      val entityChange = coroutineScope.async {
        val operation = operation(event)
        change {
          // shared should not be used here, otherwise an exception is going to be thrown during rebase
          // `mutate` should decide when to use `shared`
          // TODO check that markups work // shared to mutate shared document components (e.g., AdMarkupModel)
          //shared { // shared to mutate shared document components (e.g., AdMarkupModel)
            entity.mutate(this, OpenMap { set(DocToEntityUpdate, true) }) {
              edit(operation)
            }
          //}
        }
        //TODO we should not wait for
        awaitCommitted()
      }
      // TODO: cannot replace with runWithModalProgressBlocking because pumping events ruins the models
      runBlocking { entityChange.await() }
      if (EDT.isCurrentThreadEdt()) {
        ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
      }
    }

    private suspend fun entityToDocumentImplChange(op: Operation) {
      edtWriteAction {
        CommandProcessor.getInstance().executeCommand(
          null,
          Runnable {
            val document = documentRef.get()
            if (document != null) {
              var offset = 0
              for (item in op.ops) {
                when (item) {
                  is Op.Replace -> {
                    document.replaceString(
                      offset,
                      offset + item.delete.length,
                      item.insert,
                    )
                    offset += item.insert.length
                  }
                  is Op.Retain -> {
                    offset += item.len.toInt()
                  }
                }
              }
            }
          },
          null,
          null,
        )
      }
    }

    private fun operation(event: DocumentEvent): Operation {
      val oldFragment = event.oldFragment.toString()
      val newFragment = event.newFragment.toString()
      val lengthBefore = event.document.textLength - newFragment.length + oldFragment.length
      return Operation.replaceAt(
        offset = event.offset.toLong(),
        oldText = oldFragment,
        newText = newFragment,
        totalLength = lengthBefore.toLong(),
      )
    }

    override fun toString(): String {
      return "DocToEntitySynchronizer($debugName, entity=$entity)"
    }
  }
}
