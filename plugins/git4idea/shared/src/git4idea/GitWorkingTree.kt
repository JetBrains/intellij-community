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
  /**
   * A locked working tree is never pruned, i.e., removed from a list of existing working trees.
   * Also, locking prevents working trees from being moved or deleted.
   * Locking is useful for working trees on portable devices or network shares which are not always mounted.
   *
   * @see isPrunable
   */
  val isLocked: Boolean = false,
  /**
   * Means that the working tree can be pruned by the prune command.
   * This working tree is not considered valid by git, and its administrative files in `$GIT_DIR/worktrees` are considered stale and
   * a subject to remove on `git worktree prune` command or on eventual automatic cleanup (see `gc.worktreePruneExpire` in git-config).
   *
   * @see isLocked
   */
  val isPrunable: Boolean = false,
) {

  constructor(
    path: @NlsSafe String,
    fullBranchName: @NlsSafe String?,
    isMain: Boolean,
    isCurrent: Boolean,
    isLocked: Boolean = false,
    isPrunable: Boolean = false,
  ) :
    this(
      VcsContextFactory.getInstance().createFilePath(path, true),
      if (fullBranchName == null) null else GitStandardLocalBranch(fullBranchName),
      isMain,
      isCurrent,
      isLocked,
      isPrunable,
    )

}

@OptIn(ExperimentalSerializationApi::class)
internal object GitWorkingTreeSerializer : KSerializer<GitWorkingTree> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("git4idea.GitWorkingTree") {
    element("path", PrimitiveSerialDescriptor("path", PrimitiveKind.STRING))
    element("branch", SerialDescriptor("branch", GitStandardLocalBranch.serializer().descriptor))
    element("main", PrimitiveSerialDescriptor("main", PrimitiveKind.BOOLEAN))
    element("current", PrimitiveSerialDescriptor("current", PrimitiveKind.BOOLEAN))
    element("locked", PrimitiveSerialDescriptor("locked", PrimitiveKind.BOOLEAN))
    element("prunable", PrimitiveSerialDescriptor("prunable", PrimitiveKind.BOOLEAN))
  }

  override fun serialize(encoder: Encoder, value: GitWorkingTree) {
    val composite = encoder.beginStructure(descriptor)
    composite.encodeStringElement(descriptor, 0, value.path.path)
    composite.encodeNullableSerializableElement(descriptor, 1, GitStandardLocalBranch.serializer(), value.currentBranch)
    composite.encodeBooleanElement(descriptor, 2, value.isMain)
    composite.encodeBooleanElement(descriptor, 3, value.isCurrent)
    composite.encodeBooleanElement(descriptor, 4, value.isLocked)
    composite.encodeBooleanElement(descriptor, 5, value.isPrunable)
    composite.endStructure(descriptor)
  }

  override fun deserialize(decoder: Decoder): GitWorkingTree {
    val dec = decoder.beginStructure(descriptor)
    var path: String? = null
    var currentBranch: GitStandardLocalBranch? = null
    var isMain: Boolean? = null
    var isCurrent: Boolean? = null
    var isLocked: Boolean? = null
    var isPrunable: Boolean? = null
    var loop = true

    while (loop) {
      when (val index = dec.decodeElementIndex(descriptor)) {
        CompositeDecoder.DECODE_DONE -> loop = false
        0 -> path = dec.decodeStringElement(descriptor, 0)
        1 -> currentBranch = dec.decodeNullableSerializableElement(descriptor, 1, GitStandardLocalBranch.serializer())
        2 -> isMain = dec.decodeBooleanElement(descriptor, 2)
        3 -> isCurrent = dec.decodeBooleanElement(descriptor, 3)
        4 -> isLocked = dec.decodeBooleanElement(descriptor, 4)
        5 -> isPrunable = dec.decodeBooleanElement(descriptor, 5)
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
      isLocked = isLocked ?: throw SerializationException("Field 'isLocked' is missing"),
      isPrunable = isPrunable ?: throw SerializationException("Field 'isPrunable' is missing"),
    )
  }
}