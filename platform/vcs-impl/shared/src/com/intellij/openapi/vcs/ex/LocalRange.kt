// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeWithMe.ClientId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.jetbrains.annotations.ApiStatus

/**
 * Local line status tracker range data that also carries changelist and exclusion state.
 */
@Serializable(with = LocalRange.Serializer::class)
class LocalRange @ApiStatus.Internal constructor(
  line1: Int,
  line2: Int,
  vcsLine1: Int,
  vcsLine2: Int,
  innerRanges: List<InnerRange>?,
  override val clientIds: List<ClientId>,
  val changelistId: String,
  val exclusionState: RangeExclusionState
) : Range(line1, line2, vcsLine1, vcsLine2, innerRanges), LstLocalRange {
  init {
    if (exclusionState is RangeExclusionState.Partial) {
      exclusionState.validate(vcsLine2 - vcsLine1, line2 - line1)
    }
  }

  private object Serializer : KSerializer<LocalRange> {
    private const val LINE1 = 0
    private const val LINE2 = 1
    private const val VCS_LINE1 = 2
    private const val VCS_LINE2 = 3
    private const val INNER_RANGES = 4
    private const val CHANGELIST_ID = 5
    private const val EXCLUSION = 6

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LocalRange") {
      element<Int>("line1")
      element<Int>("line2")
      element<Int>("vcsLine1")
      element<Int>("vcsLine2")
      element<List<InnerRange>?>("innerRanges", isOptional = true)
      element<String>("changelistId")
      element<RangeExclusionState>("exclusion")
    }

    override fun serialize(encoder: Encoder, value: LocalRange) {
      encoder.encodeStructure(descriptor) {
        encodeIntElement(descriptor, LINE1, value.line1)
        encodeIntElement(descriptor, LINE2, value.line2)
        encodeIntElement(descriptor, VCS_LINE1, value.vcsLine1)
        encodeIntElement(descriptor, VCS_LINE2, value.vcsLine2)
        val innerSer = ListSerializer(InnerRange.serializer())
        val ir = value.innerRanges
        if (ir != null) {
          encodeSerializableElement(descriptor, INNER_RANGES, innerSer, ir)
        }
        encodeStringElement(descriptor, CHANGELIST_ID, value.changelistId)
        encodeSerializableElement(descriptor, EXCLUSION, RangeExclusionState.serializer(), value.exclusionState)
      }
    }

    override fun deserialize(decoder: Decoder): LocalRange {
      var line1 = 0
      var line2 = 0
      var vcsLine1 = 0
      var vcsLine2 = 0
      var innerRanges: List<InnerRange>? = null
      var changelistId = ""
      var exclusionState: RangeExclusionState = RangeExclusionState.Included

      decoder.decodeStructure(descriptor) {
        while (true) {
          when (decodeElementIndex(descriptor)) {
            LINE1 -> line1 = decodeIntElement(descriptor, LINE1)
            LINE2 -> line2 = decodeIntElement(descriptor, LINE2)
            VCS_LINE1 -> vcsLine1 = decodeIntElement(descriptor, VCS_LINE1)
            VCS_LINE2 -> vcsLine2 = decodeIntElement(descriptor, VCS_LINE2)
            INNER_RANGES -> innerRanges = decodeSerializableElement(descriptor, INNER_RANGES, ListSerializer(InnerRange.serializer()))
            CHANGELIST_ID -> changelistId = decodeStringElement(descriptor, CHANGELIST_ID)
            EXCLUSION -> exclusionState = decodeSerializableElement(descriptor, EXCLUSION, RangeExclusionState.serializer())
            else -> break
          }
        }
      }

      return LocalRange(
        line1 = line1,
        line2 = line2,
        vcsLine1 = vcsLine1,
        vcsLine2 = vcsLine2,
        innerRanges = innerRanges,
        clientIds = emptyList(), // clientIds are not serialized
        changelistId = changelistId,
        exclusionState = exclusionState
      )
    }
  }
}

@ApiStatus.Internal
fun List<LocalRange>.changesInChangeList(changeListId: String): List<LocalRange> = filter { it.changelistId == changeListId }

@ApiStatus.Internal
fun List<LocalRange>.countIncludedChanges(): Int = sumOf { it.exclusionState.countAffectedVisibleChanges(true) }

@ApiStatus.Internal
fun List<LocalRange>.countAllChanges(): Int = sumOf { it.exclusionState.countAffectedVisibleChanges(false) }
