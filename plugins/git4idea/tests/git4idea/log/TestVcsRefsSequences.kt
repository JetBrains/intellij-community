// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefsContainer

internal class TestVcsRefsSequences(val refs: Collection<VcsRef>) : VcsRefsContainer {
  override fun branches(): Sequence<VcsRef> {
    return refs.asSequence().filter { it.type.isBranch }
  }

  override fun tags(): Sequence<VcsRef> {
    return refs.asSequence().filterNot { it.type.isBranch }
  }
}
