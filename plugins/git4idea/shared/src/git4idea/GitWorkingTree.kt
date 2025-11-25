// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import kotlinx.serialization.ExperimentalSerializationApi
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

/**
 * [currentBranch] is null when working tree is detached
 */
@Serializable(GitWorkingTreeSerializer::class)
data class GitWorkingTree(
  val path: @NlsSafe FilePath,
  val currentBranch: GitStandardLocalBranch?,
  val isMain: Boolean,
  val isCurrent: Boolean,
) {

  constructor(path: @NlsSafe String, fullBranchName: @NlsSafe String?, isMain: Boolean, isCurrent: Boolean) :
    this(
      VcsContextFactory.getInstance().createFilePath(path, true),
      if (fullBranchName == null) null else GitStandardLocalBranch(fullBranchName),
      isMain,
      isCurrent
    )

}

@OptIn(ExperimentalSerializationApi::class)
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
    composite.encodeNullableSerializableElement(descriptor, 1, GitStandardLocalBranch.serializer(), value.currentBranch)
    composite.encodeBooleanElement(descriptor, 2, value.isMain)
    composite.encodeBooleanElement(descriptor, 3, value.isCurrent)
    composite.endStructure(descriptor)
  }

  override fun deserialize(decoder: Decoder): GitWorkingTree {
    val dec = decoder.beginStructure(descriptor)
    var path: String? = null
    var currentBranch: GitStandardLocalBranch? = null
    var isMain: Boolean? = null
    var isCurrent: Boolean? = null
    var loop = true

    while (loop) {
      when (val index = dec.decodeElementIndex(descriptor)) {
        CompositeDecoder.DECODE_DONE -> loop = false
        0 -> path = dec.decodeStringElement(descriptor, 0)
        1 -> currentBranch = dec.decodeNullableSerializableElement(descriptor, 1, GitStandardLocalBranch.serializer())
        2 -> isMain = dec.decodeBooleanElement(descriptor, 2)
        3 -> isCurrent = dec.decodeBooleanElement(descriptor, 3)
        else -> throw SerializationException("Unknown index $index")
      }
    }
    dec.endStructure(descriptor)

    if (path == null) {
      throw SerializationException("Field 'path' is missing")
    }
    return GitWorkingTree(
      path = LocalFilePath(path, true),
      currentBranch = currentBranch,
      isMain = isMain ?: throw SerializationException("Field 'isMain' is missing"),
      isCurrent = isCurrent ?: throw SerializationException("Field 'isCurrent' is missing"),
    )
  }
}