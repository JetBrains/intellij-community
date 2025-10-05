// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.git.ref.GitRefUtil
import git4idea.repo.GitRemote
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*

@Serializable(with = GitStandardRemoteBranchSerializer::class)
class GitStandardRemoteBranch(remote: GitRemote, nameAtRemote: String) :
  GitRemoteBranch("${remote.name}/${GitRefUtil.stripRefsPrefix(nameAtRemote)}", remote) {
  override val fullName: @NlsSafe String
    get() = REFS_REMOTES_PREFIX + name

  override val nameForRemoteOperations: String = GitRefUtil.stripRefsPrefix(nameAtRemote)

  override val nameForLocalOperations: String = name

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    if (!super.equals(o)) return false

    val branch = o as GitStandardRemoteBranch

    if (this.nameForRemoteOperations != branch.nameForRemoteOperations) return false
    if (remote != branch.remote) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + remote.hashCode()
    result = 31 * result + nameForRemoteOperations.hashCode()
    return result
  }

  override fun compareTo(o: GitReference?): Int {
    if (o is GitStandardRemoteBranch) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }
}


private object GitStandardRemoteBranchSerializer : KSerializer<GitStandardRemoteBranch> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("git4idea.GitStandardRemoteBranch") {
    element("nameForRemoteOperations", String.serializer().descriptor)
    element("remote", GitRemote.serializer().descriptor)
  }

  override fun serialize(encoder: Encoder, value: GitStandardRemoteBranch) {
    encoder.encodeStructure(descriptor) {
      encodeStringElement(descriptor, 0, value.nameForRemoteOperations)
      encodeSerializableElement(descriptor, 1, GitRemote.serializer(), value.remote)
    }
  }

  override fun deserialize(decoder: Decoder): GitStandardRemoteBranch {
    var nameAtRemote = ""
    lateinit var remote: GitRemote
    decoder.decodeStructure(descriptor) {
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> nameAtRemote = decodeStringElement(descriptor, 0)
          1 -> remote = decodeSerializableElement(descriptor, 1, GitRemote.serializer())
          CompositeDecoder.DECODE_DONE -> break
          else -> error("Unexpected index: $index")
        }
      }
    }
    return GitStandardRemoteBranch(remote, nameAtRemote)
  }
}