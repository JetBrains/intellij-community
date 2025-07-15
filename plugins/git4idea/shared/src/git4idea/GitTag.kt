// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.vcs.git.ref.GitRefUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import org.jetbrains.annotations.NonNls

@Serializable(with = GitTagSerializer::class)
class GitTag(name: String) : GitReference(GitRefUtil.stripRefsPrefix(name)) {
  override val fullName: String
    get() = REFS_TAGS_PREFIX + name

  override fun compareTo(o: GitReference?): Int {
    if (o is GitTag) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }

  companion object {
    const val REFS_TAGS_PREFIX: @NonNls String = "refs/tags/"
  }
}

private object GitTagSerializer: GitReferenceSimpleSerializer<GitTag>("git4idea.GitTag") {
  override fun deserialize(decoder: Decoder): GitTag = GitTag(decodeName(decoder))
}