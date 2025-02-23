// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import andel.operation.Operation
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.platform.pasta.common.DocumentEntity
import fleet.kernel.awaitCommitted
import fleet.kernel.change
import fleet.util.UID
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
internal data class AdDocumentSynchronizer(
  private val documentId: Any, // RdDocumentId
  private val entityId: UID,
  private val coroutineScope: CoroutineScope,
  private val entityDeferred: Deferred<DocumentEntity>,
): PrioritizedDocumentListener {

  override fun getPriority(): Int = Int.MIN_VALUE + 1

  override fun documentChanged(event: DocumentEvent) {
    val entityChange = coroutineScope.async {
      val operation = operation(event)
      val entity = entityDeferred.await()
      change { // it is a shared change
        entity.mutate(this, OpenMap.empty()) {
          edit(operation)
        }
      }
      awaitCommitted()
    }
    // TODO: pumping events in runWithModalProgressBlocking ruins the models
    //@Suppress("HardCodedStringLiteral")
    //runWithModalProgressBlocking(
    //  ModalTaskOwner.guess(),
    //  "Shared document entity synchronization $entityId",
    //) { entityChange.await() }
    runBlocking { entityChange.await() }
    ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
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
