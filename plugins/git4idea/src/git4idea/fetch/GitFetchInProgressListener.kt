// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.util.messages.Topic

internal interface GitFetchInProgressListener {
  /**
   * Called when starting the fetch if there is no other fetch in progress at the moment
   */
  fun fetchStarted()

  /**
   * Called when all fetch tasks are finished and there is no more fetch in progress
   *
   * See [GitFetchSupport.isFetchRunning]
   */
  fun fetchFinished()

  companion object {
    @Topic.ProjectLevel
    @JvmStatic
    val TOPIC: Topic<GitFetchInProgressListener> = Topic(GitFetchInProgressListener::class.java)
  }
}