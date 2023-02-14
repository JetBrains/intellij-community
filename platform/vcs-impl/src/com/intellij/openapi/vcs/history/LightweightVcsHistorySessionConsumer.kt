// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history

import com.intellij.openapi.vcs.VcsException
import com.intellij.util.Consumer

internal class LightweightVcsHistorySessionConsumer(private val myRevisionConsumer: Consumer<VcsFileRevision>) : VcsHistorySessionConsumer {
  private var exception: VcsException? = null

  override fun reportCreatedEmptySession(session: VcsAbstractHistorySession) {
    for (revision in session.revisionList) {
      myRevisionConsumer.consume(revision)
    }
  }

  override fun acceptRevision(revision: VcsFileRevision) {
    myRevisionConsumer.consume(revision)
  }

  override fun reportException(e: VcsException) {
    exception = e
  }

  override fun finished() {}

  @Throws(VcsException::class)
  fun throwIfError() {
    if (exception != null) throw VcsException(exception)
  }
}