// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.editor.DocumentComponent
import andel.operation.Operation
import andel.text.Text
import com.intellij.platform.pasta.common.DocumentComponentEntity
import com.intellij.platform.pasta.common.DocumentComponentEntity.Companion.DocumentAttr
import com.intellij.platform.pasta.common.DocumentEntity
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.requireChangeScope
import fleet.kernel.Durable
import fleet.kernel.DurableEntityType
import fleet.util.UID


// needed to be public to register in EditorEntityTypeProvider
class AdMarkupEntity(override val eid: EID) : DocumentComponentEntity<DocumentComponent> {

  val uid: UID by Durable.Id
  internal val markupStorage: AdMarkupStorage by MarkupStorageAttr

  companion object : DurableEntityType<AdMarkupEntity>(
    AdMarkupEntity::class.java.name,
    "com.intellij.platform.editor",
    ::AdMarkupEntity,
    DocumentComponentEntity,
  ) {
    internal val MarkupStorageAttr: Required<AdMarkupStorage> = requiredValue("markupStorage", AdMarkupStorage.serializer())

    internal fun empty(uid: UID, documentEntity: DocumentEntity): AdMarkupEntity = requireChangeScope {
      AdMarkupEntity.new {
        it[Durable.Id] = uid
        it[DocumentAttr] = documentEntity
        it[MarkupStorageAttr] = AdMarkupStorage(documentEntity.text)
      }
    }
  }

  override fun asComponent(changeScope: ChangeScope): DocumentComponent {
    val entity = this
    return object : DocumentComponent {
      override fun edit(before: Text, after: Text, edit: Operation) {
        changeScope.run {
          entity[MarkupStorageAttr] = markupStorage.edit(after, edit)
        }
      }
    }
  }
}
