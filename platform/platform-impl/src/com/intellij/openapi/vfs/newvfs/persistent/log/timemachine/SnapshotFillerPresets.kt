// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.AttributesOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.ContentsOperation.Companion.contentRecordId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTagsMask
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTagsMask.Companion.union
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.AttributeDataMap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.FillInVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.FillInVfsSnapshot.FillInVirtualFileSnapshot.FillInProperty
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.isFormed
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.PropertyOverwriteRule
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.isRelevantAndModifies
import kotlinx.collections.immutable.toImmutableMap

object SnapshotFillerPresets {
  class RulePropertyRelation<T>(val modificationRule: PropertyOverwriteRule<T>,
                                val property: FillInVirtualFileSnapshot.() -> FillInProperty<T>) {
    fun fillInIfModifies(operation: VfsOperation<*>, getFile: () -> FillInVirtualFileSnapshot) {
      val ifRelevantAndModifies = modificationRule.isRelevantAndModifies
      operation.ifRelevantAndModifies {
        getFile().property().fillIn(it.let(State::Ready))
      }
    }

    companion object {
      fun <T> RulePropertyRelation<T>.constrain(condition: VfsOperation<*>.(T) -> Boolean) = RulePropertyRelation(
        PropertyOverwriteRule(modificationRule.relevantOperations, modificationRule.propertyModifier.andIf(condition)),
        property
      )
    }
  }

  object RulePropertyRelations {
    val nameId = RulePropertyRelation(VfsModificationContract.nameId, FillInVirtualFileSnapshot::nameId)
    val parentId = RulePropertyRelation(VfsModificationContract.parentId, FillInVirtualFileSnapshot::parentId)
    val length = RulePropertyRelation(VfsModificationContract.length, FillInVirtualFileSnapshot::length)
    val timestamp = RulePropertyRelation(VfsModificationContract.timestamp, FillInVirtualFileSnapshot::timestamp)
    val flags = RulePropertyRelation(VfsModificationContract.flags, FillInVirtualFileSnapshot::flags)
    val contentRecordId = RulePropertyRelation(VfsModificationContract.contentRecordId, FillInVirtualFileSnapshot::contentRecordId)
    val attributeRecordId = RulePropertyRelation(VfsModificationContract.attributeRecordId, FillInVirtualFileSnapshot::attributesRecordId)
    private val recordAllocationExists = RulePropertyRelation(
      PropertyOverwriteRule(VfsOperationTagsMask(VfsOperationTag.REC_ALLOC)) { setValue ->
        if (this !is VfsOperation.RecordsOperation.AllocateRecord)
          throw AssertionError("operation $this does not allocate record")
        if (result.hasValue) setValue(true)
      },
      FillInVirtualFileSnapshot::recordAllocationExists
    )

    val allProperties = RulePropertyRelations.run {
      listOf(nameId, parentId, length, timestamp, flags, contentRecordId, attributeRecordId, recordAllocationExists)
    }
  }

  data class Filler(val relevantOperations: VfsOperationTagsMask, val fillIn: VfsOperation<*>.(FillInVfsSnapshot) -> Unit)

  fun Filler.constrain(condition: VfsOperation<*>.() -> Boolean) = Filler(relevantOperations) {
    if (condition()) fillIn(it)
  }

  fun Collection<Filler>.sum(): Filler = Filler(map { it.relevantOperations }.union()) { snapshot ->
    forEach { filler -> filler.fillIn(this, snapshot) }
  }

  fun Collection<RulePropertyRelation<*>>.buildFiller(): Filler {
    val relevantMask = map { it.modificationRule.relevantOperations }.union()
    return Filler(relevantMask) filler@{ snapshot ->
      if (relevantMask.contains(tag)) {
        this as VfsOperation.RecordsOperation
        val fileId = fileId ?: return@filler
        forEach { relation ->
          relation.fillInIfModifies(this) { snapshot.getFileById(fileId) }
        }
      }
    }
  }

  fun RulePropertyRelation<*>.toFiller(): Filler = listOf(this).buildFiller()

  val attributesFiller = Filler(VfsModificationContract.attributeData.relevantOperations) { snapshot ->
    VfsModificationContract.attributeData.isRelevantAndModifies(this) { overwriteData ->
      val fileId = (this as? VfsOperation.AttributesOperation)?.fileId ?: (this as? VfsOperation.RecordsOperation)?.fileId
      val file = snapshot.getFileById(fileId ?: return@isRelevantAndModifies)
      if (overwriteData.enumeratedAttributeFilter == null) {
        assert(overwriteData.data == null) // deletion
        if (!file.attributesFinished) {
          file.attributeDataMap.fillIn(AttributeDataMap.of(file.formingAttributesDataMap.toImmutableMap(), true).let(State::Ready))
          file.formingAttributesDataMap.clear()
        }
      }
      else if (!file.attributesFinished) {
        file.formingAttributesDataMap.putIfAbsent(overwriteData.enumeratedAttributeFilter, overwriteData.data!!) // write must have data
      }
    }
  }

  val contentFiller = Filler(VfsModificationContract.content.relevantOperations) { snapshot ->
    VfsModificationContract.content.isRelevantAndModifies(this) {
      this as VfsOperation.ContentsOperation
      val restorationSeq = snapshot.getContentRestorationSequenceBuilderFor(contentRecordId ?: return@isRelevantAndModifies)
      if (!restorationSeq.isFormed) {
        when (it) {
          is VfsModificationContract.ContentOperation.Modify -> restorationSeq.prependModification(it)
          is VfsModificationContract.ContentOperation.Set -> restorationSeq.setInitial(it)
        }
      }
    }
  }

  val everything: Filler = listOf(RulePropertyRelations.allProperties.buildFiller(), attributesFiller, contentFiller).sum()
}