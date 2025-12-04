// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(GitWorkingTreeSerializer::class)
data class GitWorkingTree(
  val path: @NlsSafe FilePath,
  val currentBranch: @NlsSafe GitStandardLocalBranch,
  val isMain: Boolean,
  val isCurrent: Boolean,
) {

  constructor(path: @NlsSafe String, fullBranchName: @NlsSafe String, isMain: Boolean, isCurrent: Boolean) :
    this(LocalFilePath(path, true), GitStandardLocalBranch(fullBranchName), isMain, isCurrent)

}

internal object GitWorkingTreeSerializer : KSerializer<GitWorkingTree> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("git4idea.GitWorkingTree") {
    element("path", PrimitiveSerialDescriptor("path", PrimitiveKind.STRING))
    element("branch", SerialDescriptor("branch", GitStandardLocalBranch.serializer().descriptor))
    element("main", PrimitiveSerialDescriptor("main", PrimitiveKind.BOOLEAN))
    element("current", PrimitiveSerialDescriptor("current", PrimitiveKind.BOOLEAN))
  }

  override fun serialize(encoder: Encoder, value: GitWorkingTree) {
    val composite = encoder.beginStructure(descriptor)
    composite.encodeStringElement(descriptor, 0, value.path.path)
    composite.encodeSerializableElement(descriptor, 1, GitStandardLocalBranch.serializer(), value.currentBranch)
    composite.encodeBooleanElement(descriptor, 2, value.isMain)
    composite.encodeBooleanElement(descriptor, 3, value.isCurrent)
    composite.endStructure(descriptor)
  }

  override fun deserialize(decoder: Decoder): GitWorkingTree {
    val dec = decoder.beginStructure(descriptor)
    var path: String? = null
    var fullBranchName: String? = null
    var isMain: Boolean? = null
    var isCurrent: Boolean? = null
    var loop = true

    while (loop) {
      when (val index = dec.decodeElementIndex(descriptor)) {
        CompositeDecoder.DECODE_DONE -> loop = false
        0 -> path = dec.decodeStringElement(descriptor, 0)
        1 -> fullBranchName = dec.decodeStringElement(descriptor, 1)
        2 -> isMain = dec.decodeBooleanElement(descriptor, 2)
        3 -> isCurrent = dec.decodeBooleanElement(descriptor, 3)
        else -> throw SerializationException("Unknown index $index")
      }
    }
    dec.endStructure(descriptor)

    return GitWorkingTree(
      path = path ?: throw SerializationException("Field 'path' is missing"),
      fullBranchName = fullBranchName ?: throw SerializationException("Field 'branch' is missing"),
      isMain = isMain ?: throw SerializationException("Field 'isMain' is missing"),
      isCurrent = isCurrent ?: throw SerializationException("Field 'isCurrent' is missing"),
    )
  }
}