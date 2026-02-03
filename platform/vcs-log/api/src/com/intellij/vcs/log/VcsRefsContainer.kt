// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

interface VcsRefsContainer {
  fun branches(): Sequence<VcsRef>

  fun tags(): Sequence<VcsRef>

  fun allRefs(): Sequence<VcsRef> = branches() + tags()
}
