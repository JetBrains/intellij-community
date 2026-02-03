// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import andel.operation.Operation
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.util.ThreadLocalRhizomeDB
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ui.EDT
import fleet.kernel.change
import fleet.kernel.rebase.awaitCommitted
import fleet.kernel.rebase.shared
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


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
          cs,
        )
      )
    }
    return cs
  }

  private class DocToEntitySynchronizer(
    private val debugName: String,
    private val entity: DocumentEntity,
    //private val document: DocumentEx,
    private val coroutineScope: CoroutineScope
  ) : PrioritizedDocumentListener {

    //private var documentChanging = false

    //init {
    //  coroutineScope.launch {
    //    var initial = true
    //    entity.asQuery()[DocumentEntity.TextAttr].collect { text ->
    //      // TODO do we need to process first change in some cases (e.g. on the frontend)?
    //      if (initial) {
    //        initial = false
    //        return@collect
    //      }
    //
    //      writeAction {
    //        documentChanging = true
    //        try {
    //          document.setText(text.view().charSequence())
    //        }
    //        finally {
    //          documentChanging = false
    //        }
    //      }
    //    }
    //  }
    //}

    override fun getPriority(): Int = Int.MIN_VALUE + 1

    override fun documentChanged(event: DocumentEvent) {
      //if (documentChanging) return

      val entityChange = coroutineScope.async {
        val operation = operation(event)
        change {
          // shared should not be used here, otherwise an exception is going to be thrown during rebase
          // `mutate` should decide when to use `shared`
          // TODO check that markups work // shared to mutate shared document components (e.g., AdMarkupModel)
          shared { // shared to mutate shared document components (e.g., AdMarkupModel)
            entity.mutate(this, OpenMap.empty()) {
              edit(operation)
            }
          }
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
