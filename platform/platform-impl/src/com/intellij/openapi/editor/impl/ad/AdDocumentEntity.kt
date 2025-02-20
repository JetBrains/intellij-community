// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import andel.editor.AnchorId
import andel.editor.RangeMarkerId
import andel.intervals.AnchorStorage
import andel.operation.EditLog
import andel.text.Text
import andel.text.TextRange
import com.jetbrains.rhizomedb.Attributes
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.requireChangeScope
import fleet.kernel.DurableEntityType
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

private typealias Att<T> = Attributes<AdDocumentEntity>.Required<T>

@Experimental
@Internal
class AdDocumentEntity(override val eid: EID) : Entity {
  val text: Text by TextAttr
  val modStamp: Long by ModStampAttr
  val editLog: EditLog by EditLogAttr
  val anchorStorage: AnchorStorage by AnchorStorageAttr

  companion object : DurableEntityType<AdDocumentEntity>(AdDocumentEntity::class, ::AdDocumentEntity) {
    val TextAttr: Att<Text> = requiredValue("text", Text.serializer())
    val ModStampAttr: Att<Long> = requiredValue("modStamp", Long.serializer())
    val EditLogAttr: Att<EditLog> = requiredValue("editLog", EditLog.serializer())
    val AnchorStorageAttr: Att<AnchorStorage> = requiredValue("anchorStorage", AnchorStorage.serializer())

    fun fromString(text: String, modStamp: Long): AdDocumentEntity = requireChangeScope {
      AdDocumentEntity.new {
        it[TextAttr] = Text.fromString(text)
        it[ModStampAttr] = modStamp
        it[EditLogAttr] = EditLog.empty()
        it[AnchorStorageAttr] = AnchorStorage.empty()
      }
    }
  }

  fun asAndelDocument(): andel.editor.Document {
    return object : andel.editor.Document {
      override val text: Text get() = this@AdDocumentEntity.text
      override val timestamp: Long = editLog.timestamp
      override val edits: EditLog get() = editLog
      override fun resolveAnchor(anchorId: AnchorId): Long? = anchorStorage.resolveAnchor(anchorId)
      override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? = anchorStorage.resolveRangeMarker(markerId)
    }
  }

  override fun toString(): String {
    return "AdDocumentEntity(eid=$eid, modStamp=$modStamp, text=$text)"
  }
}
